# CI/CD 가이드

GitLab CI/CD 파이프라인 구성 문서다. 현재는 **프론트엔드(Vite 빌드 → 정적 파일 배포)** 만 다룬다.
백엔드는 Dockerfile이 올라온 뒤 이 문서에 이어서 추가한다.

---

## 1. 전체 구조

```
GitLab Push / MR
      │
      ├─ frontend/** 변경 ─┐        ├─ backend/** 변경 ─┐
      ▼                    ▼        ▼                   ▼
┌──────────────────────────────────────────────────────────┐
│  GitLab Runner (shell executor, tag: ssafy-ec2)          │
│  = 배포 서버(EC2, j15a403) 위에 직접 등록                 │
│                                                          │
│  frontend:build  npm ci → npm run build → dist/          │
│  frontend:deploy dist/ → /var/www/j15a403/frontend       │
│                                                          │
│  backend:build   ./gradlew clean build                   │
│  backend:deploy  (Dockerfile 나오면 추가)                 │
└──────────────────────────────────────────────────────────┘
                      │
                      ▼
        호스트 Nginx 가 /var/www/j15a403/frontend 를 root 로 서빙
        https://j15a403.p.ssafy.io/
```

- Runner를 배포 서버에 직접 등록하므로 **SSH 키·배포용 시크릿이 필요 없다.**
  파일 복사가 곧 로컬 복사다.
- 빌드 결과물(`dist/`)만 서버에 올라가고, Node/소스는 서버에 남지 않는다.
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
  --description "j15a403-deploy" \
  --tag-list "ssafy-ec2"
```

> `--tag-list` 값은 `.gitlab-ci.yml` 의 `tags:` 와 반드시 일치해야 한다.

### 2-2. Node 설치 (gitlab-runner 계정 기준)

shell executor는 `gitlab-runner` 리눅스 계정으로 명령을 실행한다.
따라서 **그 계정에서 `node`, `npm` 이 잡혀야 한다.**

```bash
sudo su - gitlab-runner
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.1/install.sh | bash
source ~/.nvm/nvm.sh
nvm install 22
nvm alias default 22
node -v   # v22.x
```

`.gitlab-ci.yml` 의 `before_script` 에서 nvm을 다시 source 한다(비로그인 셸 대비).

### 2-3. JDK 설치 (백엔드용)

shell executor는 서버에 설치된 JDK를 그대로 쓴다. `backend/ai-block-clock/build.gradle` 이
**Java toolchain 25** 를 요구하고, `settings.gradle` 에 foojay-resolver 플러그인이 없어
**Gradle이 JDK를 자동으로 내려받지 못한다.** 서버에 직접 설치해야 한다.

```bash
sudo apt-get install -y wget apt-transport-https gpg
wget -qO- https://packages.adoptium.net/artifactory/api/gpg/key/public \
  | sudo tee /etc/apt/trusted.gpg.d/adoptium.asc
echo "deb https://packages.adoptium.net/artifactory/deb $(awk -F= '/^VERSION_CODENAME/{print$2}' /etc/os-release) main" \
  | sudo tee /etc/apt/sources.list.d/adoptium.list
sudo apt-get update
sudo apt-get install -y temurin-25-jdk

java -version   # openjdk version "25"
```

`gitlab-runner` 계정에서도 잡히는지 확인한다.

```bash
sudo su - gitlab-runner -c 'java -version && echo $JAVA_HOME'
```

> `JAVA_HOME` 이 비어 있으면 `/etc/profile.d/jdk.sh` 에
> `export JAVA_HOME=/usr/lib/jvm/temurin-25-jdk-amd64` 를 추가한다.
>
> 대안: `settings.gradle` 에 `foojay-resolver-convention` 플러그인을 넣으면 toolchain을
> Gradle이 내려받는다. 다만 Gradle 자체를 띄울 JDK는 여전히 필요하므로, 서버 설치가 더 간단하다.

### 2-4. 배포 디렉터리 생성 및 권한

```bash
sudo mkdir -p /var/www/j15a403/frontend
sudo chown -R gitlab-runner:gitlab-runner /var/www/j15a403
sudo chmod 755 /var/www /var/www/j15a403
```

> CI 스크립트 안에서 `sudo` 를 쓰지 않도록, 배포 경로 소유자를 미리 `gitlab-runner` 로 넘겨둔다.
> Nginx(`www-data`)는 읽기만 하므로 755 면 충분하다.

### 2-5. rsync 설치

```bash
sudo apt-get install -y rsync
```

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
  FE_NODE_VERSION: '22'

.frontend-base:
  tags:
    - ssafy-ec2
  before_script:
    - export NVM_DIR="$HOME/.nvm"
    - . "$NVM_DIR/nvm.sh"
    - nvm use "$FE_NODE_VERSION"
    - node -v && npm -v
    - cd "$FE_DIR"

.frontend-changes: &frontend-changes
  changes:
    - frontend/**/*
    - .gitlab/ci/frontend.gitlab-ci.yml
    - .gitlab-ci.yml
  exists:
    - frontend/package.json     # 브랜치에 frontend/ 가 없으면 잡을 만들지 않는다

frontend:build:
  extends: .frontend-base
  stage: build
  script:
    - npm ci
    - npm run lint
    - npm run build            # tsc -b && vite build -> frontend/dist
    - test -f dist/index.html  # 산출물 검증
  cache:
    key:
      files:
        - frontend/package-lock.json
    paths:
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
    - ssafy-ec2
  needs:
    - frontend:build
  script:
    - mkdir -p "$FE_DEPLOY_PATH"
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

Dockerfile이 아직 없으므로 **build/test 까지만** 돌린다. 이미지 빌드·컨테이너 배포 잡은
같은 파일에 주석으로 자리를 잡아뒀다.

```yaml
variables:
  BE_DIR: backend/ai-block-clock

.backend-base:
  tags:
    - ssafy-ec2
  before_script:
    - cd "$BE_DIR"
    - chmod +x ./gradlew

.backend-changes: &backend-changes
  changes:
    - backend/**/*
    - .gitlab/ci/backend.gitlab-ci.yml
    - .gitlab-ci.yml
  exists:
    - backend/ai-block-clock/settings.gradle

backend:build:
  extends: .backend-base
  stage: build
  script:
    - ./gradlew clean build --no-daemon
  cache:
    key:
      files:
        - backend/ai-block-clock/settings.gradle
        - backend/ai-block-clock/build.gradle
    paths:
      - backend/ai-block-clock/.gradle/
  artifacts:
    name: "backend-libs-$CI_COMMIT_SHORT_SHA"
    paths:
      - backend/ai-block-clock/**/build/libs/*.jar
    reports:
      junit:
        - backend/ai-block-clock/**/build/test-results/test/TEST-*.xml
    expire_in: 1 week
    when: always              # 테스트 실패해도 리포트는 올린다
  rules:
    - if: '$CI_PIPELINE_SOURCE == "merge_request_event"'
      <<: *backend-changes
    - if: '$CI_COMMIT_BRANCH =~ /^(feat|hotfix)\//'
      <<: *backend-changes
    - if: '$CI_COMMIT_BRANCH == "develop-BE"'
      <<: *backend-changes
    - if: '$CI_COMMIT_BRANCH == "master"'
      <<: *backend-changes
```

### 3-4. 왜 이렇게 했는지

| 항목 | 이유 |
| --- | --- |
| 파일 분리 + `include` | 프론트/백엔드 담당이 서로의 CI를 건드리지 않고 각자 파일만 수정 |
| `changes:` 규칙 | 백엔드만 수정한 커밋에서 프론트 파이프라인이 도는 것을 막는다 |
| `exists:` 규칙 | `develop-BE` 계열 브랜치에는 `frontend/` 가 아예 없다. CI 파일만 고쳐도 프론트 잡이 생겨 `cd frontend` 로 죽는 것을 막는다 |
| `feat/*`·`hotfix/*` 규칙 | MR을 열기 전에도 push만으로 빌드 검증이 돌게 한다 |
| CI 파일 자신을 `changes` 에 포함 | CI만 고친 커밋에서도 파이프라인이 돌아야 검증이 된다 |
| `workflow.rules` 의 `$CI_OPEN_MERGE_REQUESTS` | 브랜치 파이프라인 + MR 파이프라인 이중 실행 방지 |
| `npm ci` | `package-lock.json` 기준 재현 가능한 설치. `npm install` 은 lock을 갱신해버림 |
| `cache: key.files` | lock 파일이 바뀔 때만 캐시 무효화 → 평소 설치 시간 단축 |
| `artifacts` | build/deploy 잡을 분리해도 `dist/` 가 전달된다. 실패 시 산출물 확인·롤백에도 사용 |
| `rsync --delete` | Vite는 `assets/index-<hash>.js` 로 파일명이 매번 바뀐다. 단순 `cp` 만 하면 옛 파일이 계속 쌓임 |
| `test -f dist/index.html` | 빌드가 조용히 빈 결과를 내는 경우를 배포 전에 잡는다 |

### 3-5. 브랜치별 동작 요약

| 변경 폴더 | `feat/*` | `develop-FE` | `develop-BE` | `master` |
| --- | --- | --- | --- | --- |
| `frontend/**` | build | build | – | build → deploy |
| `backend/**` | build | – | build | build (배포는 TODO) |
| 둘 다 | 둘 다 build | build(FE) | build(BE) | 각각 |

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
| `Could not find a JDK / toolchain 25` | 서버에 JDK 25 미설치. [2-3](#2-3-jdk-설치-백엔드용) 참고 |
| `npm: command not found` | `before_script` 에서 nvm source 누락. `gitlab-runner` 계정으로 로그인해 `node -v` 확인 |
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

## 7. TODO — 백엔드

`backend:build`(Gradle 빌드 + 테스트)는 이미 `.gitlab/ci/backend.gitlab-ci.yml` 에 들어가 있다.
Dockerfile이 올라오면 같은 파일의 주석 블록을 활성화하고 이 문서에도 절을 추가한다.

- [x] `backend:build` — Gradle 멀티모듈 빌드 + 테스트 (JUnit 리포트 포함)
- [ ] `backend:image` — Docker 이미지 빌드 및 태깅
- [ ] `backend:deploy` — 서버에서 컨테이너 교체 (무중단 여부 결정 필요)
- [ ] DB / 시크릿을 GitLab CI/CD Variables(Masked, Protected)로 관리
- [ ] Nginx `/api/` 프록시 블록 활성화 및 헬스체크 경로 확정
