# CI/CD 가이드

GitLab CI/CD 파이프라인 구성 문서다. 프론트엔드(Vite 빌드 → 정적 파일 배포), 백엔드
(Gradle 테스트 → Docker Compose 배포), AI Runtime(Docker 이미지 → Compose 배포)을 다룬다.

AI 파이프라인의 잡 정의는 `.gitlab/ci/ai.gitlab-ci.yml` 에 있고, 이 문서에는 환경 변수
([5장](#5-환경-변수))만 정리한다.

---

## 1. 전체 구조

```
GitLab Push / MR
      │
      ├─ frontend/** 변경 ─┐        ├─ backend/** 변경 ─┐
      ▼                    ▼        ▼                   ▼
┌──────────────────────────────────────────────────────────┐
│  GitLab Runner (shell executor, tags: deploy shell)          │
│  = 배포 서버(EC2, j15a403) 위에 직접 등록                 │
│                                                          │
│  frontend:build  Node 컨테이너에서 vite build → dist/     │
│  frontend:deploy dist/ → /var/www/j15a403/frontend       │
│                                                          │
│  backend:build   JDK 컨테이너에서 gradle build → bootJar  │
│  backend:deploy  docker compose up -d --build            │
└──────────────────────────────────────────────────────────┘
                      │
                      ▼
        호스트 Nginx 가 /var/www/j15a403/frontend 를 root 로 서빙
        /api → 127.0.0.1:8080 (compose: ai-block-clock-backend)
        https://j15a403.p.ssafy.io/
```

- Runner를 배포 서버에 직접 등록하므로 **SSH 키·배포용 시크릿이 필요 없다.**
  파일 복사가 곧 로컬 복사다.
- 프론트는 빌드 결과물(`dist/`)만 배포 경로로 복사되고, 백엔드는 컨테이너로 교체된다.
- **빌드도 실행도 모두 컨테이너 안이다.** 호스트에 필요한 것은 Docker 뿐이다
  (Node·JDK·JRE 모두 불필요).
- CI 정의는 프론트/백엔드로 파일이 분리돼 있고, **변경된 폴더에 해당하는 잡만 생성**된다
  (→ [3. CI 파일 구성](#3-ci-파일-구성)).

### 브랜치 전략과의 관계

| 브랜치 | 동작 |
| --- | --- |
| `feat/*`, `hotfix/*` | 변경된 쪽 build 만 수행 (MR 열기 전 사전 검증) |
| `develop-FE` (MR 포함) | frontend build 만 수행 (빌드 깨짐 검증) |
| `develop-BE` (MR 포함) | backend build + test 만 수행 |
| `master` | build → deploy (실서버 반영) |
---

## 2. 사전 준비 (서버, 최초 1회)

### 2-1. GitLab Runner 설치 및 등록

```bash
# Runner 설치 (Ubuntu)
curl -L "https://packages.gitlab.com/install/repositories/runner/gitlab-runner/script.deb.sh" | sudo bash
sudo apt-get install -y gitlab-runner

# 등록 (GitLab 프로젝트 > Settings > CI/CD > Runners 에서 토큰 확인)
sudo gitlab-runner register \
  --url https://lab.ssafy.com/ \
  --token <PROJECT_RUNNER_TOKEN> \
  --executor shell \
  --description "a403-deploy-runner" \
  --tag-list "deploy,shell"
```

> `--tag-list` 값은 각 잡의 `tags:` 와 반드시 일치해야 한다. Runner가 `run_untagged=false`
> 이므로 태그가 하나라도 어긋나면 잡이 `pending` 에서 멈춘다(stuck).
> 현재 등록된 runner: `a403-deploy-runner` (tags: `deploy`, `shell`)

### 2-2. Docker 설치 (빌드 컨테이너용)

**프론트·백엔드 빌드 모두 컨테이너 안에서 돌린다.** 호스트에는 Node도 JDK도 설치하지
않고, 필요한 툴체인을 이미지가 들고 온다.

| 잡 | 빌드 이미지 |
| --- | --- |
| `frontend:build` | `node:22` |
| `backend:build` | `eclipse-temurin:25-jdk` |

```bash
sudo apt-get install -y docker.io
sudo usermod -aG docker gitlab-runner   # runner 가 docker 를 쓰려면 필요
sudo systemctl restart gitlab-runner    # 그룹 변경은 재시작해야 반영된다
sudo su - gitlab-runner -c 'docker ps'  # 권한 확인
```

> `build.gradle` 이 Java toolchain 25 를 요구하는데 `settings.gradle` 에 foojay-resolver가
> 없어 Gradle이 JDK를 자동으로 못 받는다. 그래서 빌드용 JDK를 컨테이너로 공급한다.

### 2-3. 프론트 배포 디렉터리 (권한)

```bash
sudo install -d -o gitlab-runner -g gitlab-runner /var/www/j15a403/frontend
sudo chmod 755 /var/www /var/www/j15a403
sudo apt-get install -y rsync
```

> CI 스크립트에서 `sudo` 를 쓰지 않도록 배포 경로 소유자를 미리 `gitlab-runner` 로 넘긴다.
> Nginx(`www-data`)는 읽기만 하므로 755 면 충분하다. 상위 디렉터리에 실행 권한(x)이
> 없으면 403이 난다.

### 2-4. 백엔드 자격 증명 (GitLab CI/CD Variables)

DB 자격 증명은 서버 파일이 아니라 **GitLab 변수**로 관리한다. Docker Compose 는
프로세스 환경변수로 `${...}` 를 보간하므로, 잡 환경에 들어오면 `--env-file` 없이 쓰인다.

**Settings > CI/CD > Variables** 에서 아래 4개를 추가한다.

| 변수 | 값 | Protected | Masked |
| --- | --- | --- | --- |
| `MYSQL_DATABASE` | `ai_block_clock` | ✓ | – |
| `MYSQL_USER` | `ai_block_clock` | ✓ | – |
| `MYSQL_PASSWORD` | 강한 비밀번호 | ✓ | ✓ |
| `MYSQL_ROOT_PASSWORD` | 강한 비밀번호 | ✓ | ✓ |

- **Protected** — 보호 브랜치·태그에서만 주입된다. `backend:deploy` 는 `master` 에서만
  도니 켜두는 게 맞다. 단 **`master` 가 GitLab에서 Protected 로 설정돼 있어야** 하고,
  아니면 변수가 비어서 잡이 실패한다.
- **Masked** — 로그에 값이 나오면 `[MASKED]` 로 가려진다. 8자 이상이고 공백·개행이 없어야
  마스킹이 걸리므로, 비밀번호는 영숫자 위주로 길게 잡는다.
- `Expand variable reference` 는 켜둔 기본값 그대로 둔다.

`backend:deploy` 는 이 네 개가 비어 있으면 `compose up` 전에 잡을 실패시킨다.
`compose.yaml` 이 `${MYSQL_PASSWORD:-local_password}` 처럼 **기본값을 갖고 있어서**,
검사가 없으면 로컬 비밀번호로 운영 DB가 조용히 떠버리기 때문이다.

> **MySQL 볼륨은 최초 1회만 초기화된다.** `mysql-data` 볼륨이 이미 있으면 나중에
> `MYSQL_PASSWORD` 를 바꿔도 실제 DB 계정 비밀번호는 바뀌지 않는다. 변경하려면
> DB 안에서 `ALTER USER` 를 하거나 볼륨을 지우고 다시 만들어야 한다.

---

## 3. CI 파일 구성

프론트/백엔드를 한 파일에 몰아넣으면 규칙이 뒤엉키므로, **도메인별로 파일을 분리**하고
루트에서 `include` 로 합친다.

```
.gitlab-ci.yml                      # stages, workflow, include 만 정의
.gitlab/ci/
├── frontend.gitlab-ci.yml          # frontend/** 변경 시에만 잡 생성
└── backend.gitlab-ci.yml           # backend/**  변경 시에만 잡 생성
```

핵심은 각 파일이 **자기 폴더의 `changes:` 규칙을 스스로 들고 있다**는 점이다.
프론트만 수정한 커밋에서는 백엔드 잡이 아예 생성되지 않는다(스킵이 아니라 미생성).

> YAML 앵커(`&`/`*`)는 파일 단위로만 유효하다. 그래서 `.frontend-changes` /
> `.backend-changes` 앵커를 각 파일 안에 따로 정의했다. 파일을 넘나들며 재사용해야
> 하면 앵커 대신 `extends:` 나 `!reference` 를 써야 한다.

### 3-1. `.gitlab-ci.yml` (루트)

```yaml
stages:
  - build
  - deploy

variables:
  GIT_DEPTH: '20'

include:
  - local: .gitlab/ci/frontend.gitlab-ci.yml
  - local: .gitlab/ci/backend.gitlab-ci.yml

workflow:
  rules:
    - if: '$CI_PIPELINE_SOURCE == "merge_request_event"'
    - if: '$CI_COMMIT_BRANCH && $CI_OPEN_MERGE_REQUESTS'
      when: never          # MR이 열린 브랜치는 MR 파이프라인만 (중복 방지)
    - if: '$CI_COMMIT_BRANCH'
```

`stages` 는 루트에서 한 번만 선언하고, 각 include 파일의 잡이 그 이름을 참조한다.

### 3-2. `.gitlab/ci/frontend.gitlab-ci.yml`

```yaml
variables:
  FE_DIR: frontend
  FE_DEPLOY_PATH: /var/www/j15a403/frontend
  FE_NODE_IMAGE: node:22

# frontend 폴더 또는 이 CI 파일이 바뀌었을 때만 잡을 만든다.
# exists 로 한 번 더 거른다 — 브랜치에 frontend/ 자체가 없으면(예: develop-BE 계열)
# CI 파일만 바뀌어도 잡을 만들지 않는다.
.frontend-changes: &frontend-changes
  changes:
    - frontend/**/*
    - .gitlab/ci/frontend.gitlab-ci.yml
    - .gitlab-ci.yml
  exists:
    - frontend/package.json

frontend:build:
  stage: build
  tags:
    - deploy
    - shell
  script:
    # -u 로 현재 사용자 권한을 유지해야 dist/ 가 root 소유로 남지 않는다
    - >
      docker run --rm
      -u "$(id -u):$(id -g)"
      -v "$CI_PROJECT_DIR":/workspace
      -w "/workspace/$FE_DIR"
      -e HOME=/workspace/.npm-home
      -e npm_config_cache=/workspace/.npm-cache
      "$FE_NODE_IMAGE"
      sh -c "npm ci && npm run lint && npm run build"
    - test -f "$FE_DIR/dist/index.html" # 산출물 검증
    - ls -al "$FE_DIR/dist/"
  cache:
    key:
      files:
        - frontend/package-lock.json
    paths:
      - .npm-cache/
      - frontend/node_modules/
  artifacts:
    name: "frontend-dist-$CI_COMMIT_SHORT_SHA"
    paths:
      - frontend/dist/
    expire_in: 1 week
  rules:
    - if: '$CI_PIPELINE_SOURCE == "merge_request_event"'
      <<: *frontend-changes
    - if: '$CI_COMMIT_BRANCH =~ /^(feat|hotfix)\//'
      <<: *frontend-changes
    - if: '$CI_COMMIT_BRANCH == "develop-FE"'
      <<: *frontend-changes
    - if: '$CI_COMMIT_BRANCH == "master"'
      <<: *frontend-changes

frontend:deploy:
  stage: deploy
  tags:
    - deploy
    - shell
  needs:
    - frontend:build
  script:
    - mkdir -p "$FE_DEPLOY_PATH"
    # --delete: Vite 는 assets/index-<hash>.js 로 파일명이 매번 바뀌므로
    #           옛 번들을 지우지 않으면 계속 쌓인다
    - rsync -av --delete "$FE_DIR/dist/" "$FE_DEPLOY_PATH/"
    - ls -al "$FE_DEPLOY_PATH/index.html"
  environment:
    name: production/frontend
    url: https://j15a403.p.ssafy.io
  rules:
    - if: '$CI_COMMIT_BRANCH == "master"'
      <<: *frontend-changes
```

### 3-3. `.gitlab/ci/backend.gitlab-ci.yml`

세 단계로 나뉜다.

| 잡 | 스테이지 | 하는 일 | 언제 |
| --- | --- | --- | --- |
| `backend:build` | build | `gradlew build` — 컴파일 + **단위 테스트** + bootJar | MR·`feat/*`·`develop-BE`·`master` |
| `backend:test:integration` | test | `gradlew integrationTest` — **통합 테스트** | master 로 들어가는 MR, `master` push |
| `backend:deploy` | deploy | `docker compose up -d --build` + 헬스체크 | `master` (현재 수동) |

```yaml
variables:
  BE_DIR: backend/ai-block-clock
  BE_APP_MODULE: core/core-api # 실행 가능한 모듈은 core-api 하나뿐
  BE_JDK_IMAGE: eclipse-temurin:25-jdk
  # 컨테이너 안에서 쓸 Gradle 홈. 워크스페이스 안에 두고 GitLab cache 로 재사용한다.
  GRADLE_USER_HOME: .gradle-home

.backend-gradle:
  tags:
    - deploy
    - shell
  before_script:
    - chmod +x "$BE_DIR/gradlew"
  cache:
    key:
      files:
        - backend/ai-block-clock/settings.gradle
        - backend/ai-block-clock/build.gradle
    paths:
      - .gradle-home/caches/
      - .gradle-home/wrapper/

.backend-changes: &backend-changes
  changes:
    - backend/**/*
    - .gitlab/ci/backend.gitlab-ci.yml
    - .gitlab-ci.yml
  exists:
    - backend/ai-block-clock/settings.gradle

backend:build:
  extends: .backend-gradle
  stage: build
  script:
    # 호스트 JDK 대신 컨테이너의 JDK 25 를 쓴다.
    # -u 로 현재 사용자 권한을 유지해야 산출물이 root 소유로 남지 않는다.
    - >
      docker run --rm
      -u "$(id -u):$(id -g)"
      -v "$CI_PROJECT_DIR":/workspace
      -w "/workspace/$BE_DIR"
      -e GRADLE_USER_HOME="/workspace/$GRADLE_USER_HOME"
      "$BE_JDK_IMAGE"
      ./gradlew clean build --no-daemon
    - ls -al "$BE_DIR/$BE_APP_MODULE/build/libs/"
  artifacts:
    name: "backend-bootjar-$CI_COMMIT_SHORT_SHA"
    paths:
      - backend/ai-block-clock/core/core-api/build/libs/*.jar
    reports:
      junit:
        - backend/ai-block-clock/**/build/test-results/test/TEST-*.xml
    expire_in: 1 week
    when: always # 테스트가 깨져도 리포트는 올린다
  rules:
    - if: '$CI_PIPELINE_SOURCE == "merge_request_event"'
      <<: *backend-changes
    - if: '$CI_COMMIT_BRANCH =~ /^(feat|hotfix)\//'
      <<: *backend-changes
    - if: '$CI_COMMIT_BRANCH == "develop-BE"'
      <<: *backend-changes
    - if: '$CI_COMMIT_BRANCH == "master"'
      <<: *backend-changes

backend:test:integration:
  extends: .backend-gradle
  stage: test
  needs:
    # 컴파일 결과를 재활용하지 않고 컨테이너에서 다시 돌린다. jar 는 필요 없다.
    - job: backend:build
      artifacts: false
  script:
    - >
      docker run --rm
      -u "$(id -u):$(id -g)"
      -v "$CI_PROJECT_DIR":/workspace
      -w "/workspace/$BE_DIR"
      -e GRADLE_USER_HOME="/workspace/$GRADLE_USER_HOME"
      "$BE_JDK_IMAGE"
      ./gradlew integrationTest --no-daemon
  artifacts:
    reports:
      junit:
        - backend/ai-block-clock/**/build/test-results/integrationTest/TEST-*.xml
    expire_in: 1 week
    when: always
  rules:
    # master 로 들어가는 MR — 여기가 게이트다. 실패하면 머지가 막힌다.
    - if: '$CI_MERGE_REQUEST_TARGET_BRANCH_NAME == "master"'
      <<: *backend-changes
    # master push — 안전망 (핫픽스 직접 푸시 등)
    - if: '$CI_COMMIT_BRANCH == "master"'
      <<: *backend-changes
    # 그 밖에는 수동으로 돌릴 수 있게 남긴다
    - if: '$CI_PIPELINE_SOURCE == "merge_request_event"'
      <<: *backend-changes
      when: manual
      allow_failure: true
    - if: '$CI_COMMIT_BRANCH == "develop-BE"'
      <<: *backend-changes
      when: manual
      allow_failure: true

backend:deploy:
  stage: deploy
  tags:
    - deploy
    - shell
  needs:
    # Dockerfile 이 이 jar 를 COPY 하므로 artifacts 를 받아야 한다.
    - job: backend:build
      artifacts: true
    # needs 는 stage 순서를 덮어쓴다. 여기에 안 적으면 통합 테스트가 끝나기 전에
    # 배포가 시작될 수 있다.
    - job: backend:test:integration
      artifacts: false
  script:
    - cd "$BE_DIR"
    # prod target 은 jar 를 COPY 만 한다. jar 가 없으면
    # "lstat ...: no such file or directory" 로만 죽어서 원인을 알기 어렵다.
    - |
      if ! ls "$BE_APP_MODULE"/build/libs/*.jar >/dev/null 2>&1; then
        echo "$BE_APP_MODULE/build/libs/ 에 jar 가 없습니다."
        echo "backend:build 의 artifacts 가 내려왔는지 확인하세요 (needs.artifacts: true)."
        exit 1
      fi
    # 자격 증명은 GitLab CI/CD Variables 에서 온다. compose 가 프로세스 환경변수로
    # ${...} 를 보간하므로 --env-file 없이 그대로 쓰인다.
    #
    # compose.yaml 에 ${MYSQL_PASSWORD:-local_password} 같은 기본값이 있어서, 변수가
    # 비어 있으면 조용히 로컬 비밀번호로 뜬다. 그 전에 잡는다.
    - |
      for v in MYSQL_DATABASE MYSQL_USER MYSQL_PASSWORD MYSQL_ROOT_PASSWORD; do
        eval "val=\$$v"
        if [ -z "$val" ]; then
          echo "$v 가 비어 있습니다. GitLab > Settings > CI/CD > Variables 를 확인하세요."
          echo "(Protected 로 설정한 변수는 보호 브랜치에서만 주입됩니다)"
          exit 1
        fi
      done
    - >
      docker compose
      -f compose.yaml -f compose.prod.yaml
      up -d --build --remove-orphans
    - docker compose -f compose.yaml -f compose.prod.yaml ps
    # compose 의 healthcheck 가 UP 이 될 때까지 기다린다
    - |
      for i in $(seq 1 40); do
        if curl -fsS http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"'; then
          echo "health OK"
          docker image prune -f
          exit 0
        fi
        sleep 3
      done
      echo "헬스체크 실패 — 컨테이너 로그:"
      docker compose -f compose.yaml -f compose.prod.yaml logs --tail=100 backend
      exit 1
  environment:
    name: production/backend
    url: https://j15a403.p.ssafy.io/api
  rules:
    - if: '$CI_COMMIT_BRANCH == "master"'
      <<: *backend-changes
      when: manual # DB 자격 증명 준비 전까지 수동. 준비되면 이 줄을 지운다
```

#### 단위 / 통합 테스트를 나눴다

테스트가 쌓이면 MR 마다 전체를 돌리는 게 부담이 된다. 그래서 갈랐다.

```
feat/* push            : 단위만              (통합 잡은 생성되지 않는다)
feat/* → develop-BE MR : 단위 + 통합(수동)
develop-BE push        : 단위 + 통합(수동)
develop-BE → master MR : 단위 + 통합         ← 게이트
master push            : 단위 + 통합         (안전망)
```

**통합 테스트를 `master` push 가 아니라 "master 로 들어가는 MR" 에서 돌린다.** push 에서
돌리면 실패해도 이미 머지된 뒤라 master 가 깨진 채로 남는다. MR 에서 돌려야 실패가
머지 자체를 막는다.

```yaml
- if: '$CI_MERGE_REQUEST_TARGET_BRANCH_NAME == "master"'   # 게이트
- if: '$CI_COMMIT_BRANCH == "master"'                      # 안전망
```

`feat/*` push 에는 해당하는 규칙이 없어 **통합 테스트 잡이 생성되지 않는다.** 수동으로도
돌릴 수 없다. `feat/*` 에서 확인하려면 MR 을 열어야 한다.

##### 분리 방식: JUnit 태그

`build.gradle` 에서 `test` 는 태그를 제외하고, `integrationTest` 는 태그만 포함한다.

```gradle
tasks.named('test') {
    useJUnitPlatform {
        excludeTags 'integration'
    }
}

tasks.register('integrationTest', Test) {
    group = 'verification'
    testClassesDirs = sourceSets.test.output.classesDirs
    classpath = sourceSets.test.runtimeClasspath
    useJUnitPlatform {
        includeTags 'integration'
    }
    shouldRunAfter tasks.named('test')
}
```

소스셋(`src/integrationTest/java`)을 나누는 방법도 있지만 태그를 골랐다.

| | 태그 | 소스셋 분리 |
| --- | --- | --- |
| 의존성 선언 | `test` 것을 그대로 씀 | 별도로 다시 선언해야 함 |
| 분류 변경 | 애노테이션 한 줄 | 파일을 옮겨야 함 |
| 강제성 | 태그를 안 붙이면 단위로 분류됨 | 위치가 곧 분류 |

**통합 테스트에는 `@Tag("integration")` 을 붙인다.** 안 붙이면 단위 테스트로 분류돼서
MR 마다 돌게 된다. 분류 기준과 리뷰 체크리스트는
[backend/ai-block-clock/docs/RULE.md](../backend/ai-block-clock/docs/RULE.md) 에 정리했다.

```java
@Tag("integration")
@SpringBootTest
class AiBlockClockApplicationTests {
```

기준: **외부 의존(DB, 컨테이너, 네트워크)이 있거나 스프링 컨텍스트를 띄우면 통합.**
기존 `AiBlockClockApplicationTests` 는 `@SpringBootTest` 로 컨텍스트 전체를 올리므로
통합으로 옮겼다.

동작 확인:

```bash
$ ./gradlew :core:core-api:test --rerun-tasks
BUILD SUCCESSFUL          # 실행된 테스트 0건 (통합만 있으니 제외됨)

$ ./gradlew clean :core:core-api:integrationTest
> Task :core:core-api:compileTestJava
> Task :core:core-api:testClasses
> Task :core:core-api:integrationTest      # test 는 돌지 않음
BUILD SUCCESSFUL
```

> `needs:` 는 `stages:` 순서를 **덮어쓴다.** `backend:deploy` 의 `needs` 에
> `backend:test:integration` 을 적지 않으면, 통합 테스트가 끝나기 전에 배포가 시작된다.

#### Dockerfile 을 target 으로 나눴다

원래 `Dockerfile` 은 단일 멀티스테이지였다 — builder 가 항상 소스를 컴파일하고 그 jar 를
런타임에 복사했다. 문제가 두 가지였다.

1. **컴파일이 2회 돈다.** `backend:build` 가 `gradlew build` 로 이미 컴파일을 끝냈는데,
   builder 스테이지가 소스를 받아 처음부터 다시 컴파일한다. Dockerfile 은 파이프라인을
   모르니 CI 의 결과물을 재활용할 방법이 없다.
2. **배포되는 산출물이 테스트를 통과한 그것이 아니다.** `gradlew build` 에는 `test` 가
   붙지만 `gradlew bootJar` 에는 붙지 않는다.

```bash
./gradlew :core:core-api:build   --dry-run | grep ':test'   # → :core:core-api:test
./gradlew :core:core-api:bootJar --dry-run | grep ':test'   # → (없음)
```

그렇다고 런타임 전용으로만 바꾸면 로컬에서 `docker compose up` 전에 `bootJar` 를 먼저
돌려야 해서 개발 편의가 나빠진다. 그래서 **target 을 나눠 둘 다 만족시킨다.**

```dockerfile
FROM eclipse-temurin:25-jre AS runtime-base   # 두 target 이 공유
FROM eclipse-temurin:25-jdk AS builder        # local 만 사용
FROM runtime-base AS prod                     # 밖에서 빌드된 jar 를 COPY
FROM runtime-base AS local                     # builder 의 jar 를 COPY (기본 target)
```

| target | 쓰는 곳 | jar 출처 | 컴파일 |
| --- | --- | --- | --- |
| `local` | `compose.yaml` — 로컬 개발 | builder 스테이지 | 컨테이너 안에서 |
| `prod` | `compose.prod.yaml` — CI 배포 | `backend:build` artifacts | 안 함 |

**BuildKit 은 target 이 의존하는 스테이지만 빌드한다.** `--target prod` 면 builder 는 아예
실행되지 않는다 — `eclipse-temurin:25-jdk` 이미지를 받지도 않는다.

```
$ docker build --no-cache --target prod .
#5 [runtime-base 1/3] FROM eclipse-temurin:25-jre
#8 [runtime-base 3/3] RUN apt-get install curl
#9 [prod 1/1] COPY core/core-api/build/libs/*.jar app.jar
   ← builder 스테이지 없음
```

로컬 개발은 **기존과 똑같다.** 사전 준비 없이 한 번에 뜬다.

```bash
docker compose up -d --build
```

같이 바뀐 것:

| 파일 | 변경 |
| --- | --- |
| `Dockerfile` | `runtime-base` / `builder` / `prod` / `local` 4개 스테이지로 분리. 상단에 변경 이력 주석 |
| `.dockerignore` | `**/build` 가 jar 까지 제외해버려서 `!core/core-api/build/libs/*.jar` 예외 추가 (prod target 용) |
| `compose.yaml` | `build.target: local` |
| `compose.prod.yaml` | `build.target: prod` |
| `.gitlab/ci/backend.gitlab-ci.yml` | `needs.artifacts: true`, `compose up` 전 jar 존재 검사 |

> 두 target 의 이미지 크기는 같다(625MB). 런타임 베이스를 공유하기 때문이다.
> jar 가 없으면 `lstat ...: no such file or directory` 로만 죽어서 원인을 알기 어렵다.
> 그래서 `backend:deploy` 가 `compose up` 전에 jar 존재를 먼저 확인한다.

#### `compose.prod.yaml`

`compose.yaml` 은 로컬 개발 기준이다 — `SPRING_PROFILES_ACTIVE: dev`, MySQL 3306 호스트 노출,
비밀번호 기본값. 운영에서 달라져야 하는 것만 override 파일로 겹친다.

```yaml
services:
  mysql:
    ports: !reset []            # 호스트에 열지 않는다. backend 는 compose 네트워크로 접근
    restart: unless-stopped

  backend:
    image: ai-block-clock-backend:${IMAGE_TAG:-latest}
    environment:
      SPRING_PROFILES_ACTIVE: prod
    ports: !override
      - "127.0.0.1:8080:8080"   # 외부는 Nginx 를 거친다
    restart: unless-stopped
```

적용 결과 확인:

```bash
docker compose -f compose.yaml -f compose.prod.yaml config | grep -E "SPRING_PROFILES|host_ip"
#   SPRING_PROFILES_ACTIVE: prod
#   host_ip: 127.0.0.1
```

> `!reset` / `!override` 는 Docker Compose 2.24+ 에서 쓸 수 있다. 없으면 `ports` 가
> **덮어쓰기가 아니라 병합**되어 MySQL 3306 이 그대로 열린 채 남는다.

### 3-4. 왜 이렇게 했는지

| 항목 | 이유 |
| --- | --- |
| 파일 분리 + `include` | 프론트/백엔드 담당이 서로의 CI를 건드리지 않고 각자 파일만 수정 |
| `changes:` 규칙 | 백엔드만 수정한 커밋에서 프론트 파이프라인이 도는 것을 막는다 |
| `exists:` 규칙 | `develop-BE` 계열 브랜치에는 `frontend/` 가 아예 없다. CI 파일만 고쳐도 프론트 잡이 생겨 `cd frontend` 로 죽는 것을 막는다 |
| `feat/*`·`hotfix/*` 규칙 | MR을 열기 전에도 push만으로 빌드 검증이 돌게 한다 |
| 빌드를 컨테이너로 | 호스트에 Node/JDK를 깔지 않는다. 툴체인 버전이 이미지 태그로 고정돼 재현성도 좋다 |
| `docker run -u $(id -u)` | 안 붙이면 산출물이 root 소유로 남아 다음 파이프라인의 `clean` 이 깨진다 |
| `GRADLE_USER_HOME` 을 워크스페이스 안에 | 컨테이너의 `/root/.gradle` 은 잡이 끝나면 사라진다. 워크스페이스에 둬야 GitLab cache로 재사용된다 |
| 배포 후 `/actuator/health` 확인 | `compose up` 이 성공해도 기동에 실패할 수 있다. 헬스체크까지 봐야 배포 성공이라 할 수 있다 |
| `needs: artifacts: true` | Dockerfile 이 런타임 전용이라 `backend:build` 의 jar 가 필요하다 |
| 자격 증명을 GitLab 변수로 | 서버에 평문 파일을 두지 않는다. 값 교체가 GitLab UI에서 끝나고, 누가 언제 바꿨는지 남는다 |
| 배포 전 변수 존재 검사 | `compose.yaml` 의 비밀번호 기본값(`local_password`)으로 운영 DB가 뜨는 것을 막는다 |
| CI 파일 자신을 `changes` 에 포함 | CI만 고친 커밋에서도 파이프라인이 돌아야 검증이 된다 |
| `workflow.rules` 의 `$CI_OPEN_MERGE_REQUESTS` | 브랜치 파이프라인 + MR 파이프라인 이중 실행 방지 |
| `npm ci` | `package-lock.json` 기준 재현 가능한 설치. `npm install` 은 lock을 갱신해버림 |
| `cache: key.files` | lock 파일이 바뀔 때만 캐시 무효화 → 평소 설치 시간 단축 |
| `artifacts` | build/deploy 잡을 분리해도 `dist/` 가 전달된다. 실패 시 산출물 확인·롤백에도 사용 |
| `rsync --delete` | Vite는 `assets/index-<hash>.js` 로 파일명이 매번 바뀐다. 단순 `cp` 만 하면 옛 파일이 계속 쌓임 |
| `test -f dist/index.html` | 빌드가 조용히 빈 결과를 내는 경우를 배포 전에 잡는다 |

### 3-5. 브랜치별 동작 요약

| 변경 폴더 | `feat/*` | `develop-*` MR | → `master` MR | `master` push |
| --- | --- | --- | --- | --- |
| `frontend/**` | build | build | build | build → deploy |
| `backend/**` | build (단위만) | build + 통합(수동) | build + **통합** | build + 통합 → deploy(수동) |

> `develop-FE`/`develop-BE` 열의 `–` 는 `exists:` 규칙 때문이다. 해당 브랜치에는
> 상대편 소스 폴더가 존재하지 않으므로 잡 자체가 생성되지 않는다.

배포 후 디렉터리 모습:

```
/var/www/j15a403/frontend/
├── index.html
├── favicon.png
└── assets/
    ├── index-a1b2c3d4.js
    └── index-e5f6g7h8.css
```

---

## 4. Nginx 설정

`/etc/nginx/sites-available/j15a403` :

```nginx
server {
    listen 80;
    server_name j15a403.p.ssafy.io;

    root /var/www/j15a403/frontend;
    index index.html;

    # SPA: react-router 경로를 새로고침해도 index.html 로 보낸다
    location / {
        try_files $uri $uri/ /index.html;
    }

    # 해시가 붙은 정적 자산은 길게 캐시
    location /assets/ {
        expires 1y;
        add_header Cache-Control "public, immutable";
    }

    # index.html 은 캐시하지 않는다 (배포 즉시 반영)
    location = /index.html {
        add_header Cache-Control "no-cache, must-revalidate";
    }

    # 백엔드 API 프록시 (Spring Boot 올라온 뒤 활성화)
    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

```bash
sudo ln -s /etc/nginx/sites-available/j15a403 /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx
```

> 프론트는 `VITE_API_BASE_URL=/api` 를 쓰므로, 같은 오리진의 `/api` 를 Nginx가
> 백엔드로 넘겨주면 CORS 설정 없이 동작한다. (개발 환경의 `vite.config.ts` proxy와 동일한 구조)

### HTTPS

```bash
sudo apt-get install -y certbot python3-certbot-nginx
sudo certbot --nginx -d j15a403.p.ssafy.io
```

---

## 5. 환경 변수

Vite는 **빌드 시점에** `VITE_*` 값을 번들에 박아 넣는다. 런타임 주입이 안 되므로
값이 다르면 빌드를 다시 해야 한다.

| 변수 | 값 | 설정 위치 |
| --- | --- | --- |
| `VITE_APP_NAME` | ABC Agent Studio | `frontend/.env.production` (커밋) |
| `VITE_API_BASE_URL` | `/api` | `frontend/.env.production` (커밋) |
| `VITE_API_PROXY_TARGET` | - | 로컬 dev 전용. 배포에는 불필요 |

비밀값이 아니므로 `.env.production` 파일을 커밋해서 관리한다.

### 백엔드

런타임에 주입되므로 빌드와 무관하다. GitLab CI/CD Variables 로 관리하며 저장소에 두지
않는다 ([2-4](#2-4-백엔드-자격-증명-gitlab-cicd-variables)). `SPRING_PROFILES_ACTIVE` 와
`DB_URL` 은 `compose.prod.yaml` / `compose.yaml` 이 조립하므로 변수로 넣지 않는다.

### AI Runtime

백엔드와 같은 방식이다. GitLab CI/CD Variables 에 넣고 `compose.yaml` 이 컨테이너
환경변수로 주입한다. 둘 다 **Masked** 로 설정한다.

| 변수 | 용도 | 비고 |
| --- | --- | --- |
| `GEMINI_API_KEY` | LLM 호출 | 없으면 `ai:deploy` 가 실패한다 |
| `ABC_SERVICE_TOKEN` | 백엔드 → AI Runtime 서비스 간 인증 | 백엔드도 같은 값을 갖고 `X-ABC-Service-Token` 헤더로 보낸다 |
| `BACKEND_NETWORK` | (선택) 백엔드 Docker 네트워크 이름 | 비우면 `ai:deploy` 가 `docker network ls` 에서 찾는다 |

**배포 트리거.** `ai:deploy` 는 수동 잡이다. `develop-AI` 와 `master` 파이프라인에서 실행 버튼으로 올린다.
MVP 기간에는 백엔드·프론트 연동을 위해 `develop-AI` 배포를 허용하며, 두 브랜치 배포는 같은 컨테이너를
덮어쓴다. 코드 프리즈(09-18) 후에는 `master` 만 남긴다.

**AI Runtime 은 Nginx 설정이 필요 없다.** 브라우저가 직접 부르지 않고 백엔드만
호출하기 때문이다. 백엔드 compose 가 만든 네트워크에 참여해 컨테이너 이름으로 통신한다.

```
백엔드 → http://ai-block-clock-ai-runtime:8000
```

포트는 `127.0.0.1:8000` 에만 바인딩한다. 외부에서 직접 열지 않는다.

**키·토큰류는 절대 `VITE_` 로 넣지 않는다** (번들에 그대로 노출됨). 필요하면
GitLab CI/CD Variables(Settings > CI/CD > Variables, Masked 체크)에 넣고 빌드 잡에서 주입한다.

---

## 6. 확인 및 트러블슈팅

### 배포 확인

```bash
curl -I https://j15a403.p.ssafy.io/            # 200
curl -I https://j15a403.p.ssafy.io/mypage      # 200 (SPA fallback)
ls -al /var/www/j15a403/frontend/
```

### 자주 나오는 문제

| 증상 | 원인 / 해결 |
| --- | --- |
| `This job is stuck` | Runner 태그 불일치. `.gitlab/ci/*.gitlab-ci.yml` 의 `tags:` 와 Runner 등록 태그 확인 |
| `docker: permission denied` | `sudo usermod -aG docker gitlab-runner` 후 runner 재시작 ([2-3](#2-3-docker--jre-설치-백엔드용)) |
| 빌드 산출물이 root 소유 | `docker run` 의 `-u "$(id -u):$(id -g)"` 누락 |
| `backend:deploy` 헬스체크 실패 | 잡 로그 끝에 컨테이너 로그가 찍힌다. 대개 Flyway 마이그레이션 또는 DB 접속 실패 |
| `MYSQL_PASSWORD 가 비어 있습니다` | 변수 미설정, 또는 Protected 변수인데 `master` 가 보호 브랜치가 아님 ([2-4](#2-4-백엔드-자격-증명-gitlab-cicd-variables)) |
| MySQL 3306 이 외부에 열려 있음 | `-f compose.prod.yaml` 누락, 또는 Compose 2.24 미만으로 `!reset` 미지원 |
| `npm ci` 가 EACCES 로 실패 | 컨테이너 `HOME` 이 안 잡힘. `-e HOME=/workspace/.npm-home` 확인 |
| `rsync: Permission denied` | `sudo chown -R gitlab-runner:gitlab-runner /var/www/j15a403` 재실행 |
| 새로고침 시 404 | Nginx `try_files ... /index.html` 누락 |
| 배포했는데 화면이 그대로 | 브라우저 캐시 or `index.html` 캐시 헤더. 위 `location = /index.html` 블록 확인 |
| 403 Forbidden | `/var/www` ~ 배포 경로까지 상위 디렉터리 실행 권한(x) 확인: `chmod 755` |
| API 404 | Nginx `/api/` 프록시 블록 확인. `proxy_pass` 끝 슬래시 유무 주의 |

### 롤백

`frontend:build` 의 artifacts가 1주일 보관되므로, GitLab 파이프라인 화면에서
이전 커밋의 build 잡 → **Job artifacts > Download** 로 받은 뒤 서버에 풀어 넣거나,
해당 커밋에서 **Run pipeline** 을 다시 돌린다.

---

## 7. 남은 작업

- [x] `frontend:build` — Node 컨테이너에서 vite 빌드, `dist/` artifacts
- [x] `backend:build` — JDK 컨테이너에서 Gradle 빌드 + 단위 테스트 (JUnit 리포트)
- [x] `backend:test:integration` — `@Tag("integration")` 통합 테스트, master 게이트
- [x] `backend:deploy` — `docker compose up -d --build` + 헬스체크 (현재 `when: manual`)
- [x] Dockerfile target 분리(local/prod) — 컴파일 중복 제거, 테스트 통과한 jar 를 배포
- [ ] 서버 준비: [2-3](#2-3-프론트-배포-디렉터리-권한) 프론트 경로 권한
- [ ] GitLab CI/CD Variables 4개 등록 + `master` 보호 브랜치 확인 ([2-4](#2-4-백엔드-자격-증명-gitlab-cicd-variables))
- [ ] `compose.prod.yaml`·`Dockerfile` 변경 백엔드 담당자 리뷰
- [ ] 검증 후 `backend:deploy` 의 `when: manual` 제거
- [ ] `frontend:deploy` 실동작 검증 (CI 파일이 `develop-FE` 로 머지될 때)
- [ ] Nginx `/api/` 프록시 블록 활성화
- [ ] 프론트 테스트 도입 시 `frontend:build` 에 추가 (현재 `package.json` 에 test 스크립트 없음)
- [ ] 무중단 배포 필요 여부 판단 (현재는 컨테이너 교체 중 수 초 다운타임)
