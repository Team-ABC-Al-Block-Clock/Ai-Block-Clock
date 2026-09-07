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
│  GitLab Runner (shell executor, tags: deploy shell)          │
│  = 배포 서버(EC2, j15a403) 위에 직접 등록                 │
│                                                          │
│  frontend:build  Node 컨테이너에서 vite build → dist/     │
│  frontend:deploy dist/ → /var/www/j15a403/frontend       │
│                                                          │
│  backend:build   JDK 컨테이너에서 gradle build → bootJar  │
│  backend:deploy  app.jar → /opt/j15a403/backend + restart│
└──────────────────────────────────────────────────────────┘
                      │
                      ▼
        호스트 Nginx 가 /var/www/j15a403/frontend 를 root 로 서빙
        /api → 127.0.0.1:8080 (systemd: j15a403-backend)
        https://j15a403.p.ssafy.io/
```

- Runner를 배포 서버에 직접 등록하므로 **SSH 키·배포용 시크릿이 필요 없다.**
  파일 복사가 곧 로컬 복사다.
- 빌드 결과물(`dist/`, `app.jar`)만 배포 경로로 옮겨진다.
- **빌드는 프론트·백엔드 모두 컨테이너 안에서 돈다.** 호스트에는 Node도 JDK도 필요 없고,
  Docker 와 bootJar 실행용 JRE 25 만 있으면 된다.
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

### 2-3. JRE 25 설치 (백엔드 실행용)

빌드는 컨테이너가 하지만, bootJar 를 **실행**하는 건 호스트다. JRE만 있으면 된다.

```bash
wget -qO- https://packages.adoptium.net/artifactory/api/gpg/key/public \
  | sudo tee /etc/apt/trusted.gpg.d/adoptium.asc
echo "deb https://packages.adoptium.net/artifactory/deb $(awk -F= '/^VERSION_CODENAME/{print$2}' /etc/os-release) main" \
  | sudo tee /etc/apt/sources.list.d/adoptium.list
sudo apt-get update && sudo apt-get install -y temurin-25-jre
sudo su - gitlab-runner -c 'java -version'
```

> **이 절은 없어질 예정이다.** `backend/ai-block-clock/` 에 멀티스테이지 Dockerfile과
> `compose.yaml` 이 들어왔으므로, `backend:deploy` 를 `docker compose` 방식으로 바꾸면
> 런타임 JRE도 컨테이너가 들고 온다. 그때 이 절과 [2-6](#2-6-백엔드-systemd-유닛)을 삭제한다.

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

### 2-6. 백엔드 systemd 유닛

`backend:deploy` 는 jar 를 갈아끼우고 `systemctl restart` 만 한다. 유닛은 미리 만들어둔다.

`/etc/systemd/system/j15a403-backend.service` :

```ini
[Unit]
Description=j15a403 backend (Spring Boot)
After=network.target

[Service]
Type=simple
User=gitlab-runner
WorkingDirectory=/opt/j15a403/backend
EnvironmentFile=/etc/j15a403/backend.env
ExecStart=/usr/bin/java -jar /opt/j15a403/backend/app.jar
SuccessExitStatus=143
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
```

DB 접속 정보는 유닛이 아니라 환경 파일에 둔다 (`db-prod.yml` 이 기본값 없이 환경 변수를 요구한다).

```bash
sudo install -d -m 700 /etc/j15a403
sudo tee /etc/j15a403/backend.env >/dev/null <<'ENV'
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=8080
DB_URL=jdbc:mysql://127.0.0.1:3306/j15a403
DB_USERNAME=...
DB_PASSWORD=...
ENV
sudo chmod 600 /etc/j15a403/backend.env

sudo install -d -o gitlab-runner -g gitlab-runner /opt/j15a403/backend
sudo systemctl daemon-reload && sudo systemctl enable j15a403-backend
```

`backend:deploy` 가 `sudo systemctl restart` 를 쓰므로, 그 명령만 비밀번호 없이 허용한다.

```bash
echo 'gitlab-runner ALL=(root) NOPASSWD: /bin/systemctl restart j15a403-backend, /bin/journalctl -u j15a403-backend *' \
  | sudo tee /etc/sudoers.d/gitlab-runner-backend
sudo chmod 440 /etc/sudoers.d/gitlab-runner-backend
```

> 전체 `sudo` 를 열지 않고 필요한 명령만 화이트리스트에 넣는다.

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

프론트와 같은 구조다 — **CI가 빌드·테스트해서 산출물(bootJar)을 artifacts로 남기고,
CD가 그걸 서버에 옮긴다.** 다른 점은 빌드를 JDK 컨테이너 안에서 돌린다는 것뿐이다.

```yaml
variables:
  BE_DIR: backend/ai-block-clock
  BE_APP_MODULE: core/core-api # 실행 가능한 모듈은 core-api 하나뿐
  BE_JDK_IMAGE: eclipse-temurin:25-jdk
  BE_DEPLOY_PATH: /opt/j15a403/backend
  BE_SERVICE: j15a403-backend
  # 컨테이너 안에서 쓸 Gradle 홈. 워크스페이스 안에 두고 GitLab cache 로 재사용한다.
  GRADLE_USER_HOME: .gradle-home

.backend-changes: &backend-changes
  changes:
    - backend/**/*
    - .gitlab/ci/backend.gitlab-ci.yml
    - .gitlab-ci.yml
  exists:
    - backend/ai-block-clock/settings.gradle

backend:build:
  stage: build
  tags:
    - deploy
    - shell
  before_script:
    - chmod +x "$BE_DIR/gradlew"
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
  cache:
    key:
      files:
        - backend/ai-block-clock/settings.gradle
        - backend/ai-block-clock/build.gradle
    paths:
      - .gradle-home/caches/
      - .gradle-home/wrapper/
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

backend:deploy:
  stage: deploy
  tags:
    - deploy
    - shell
  needs:
    - backend:build
  script:
    - install -d "$BE_DEPLOY_PATH"
    # 롤백용으로 직전 jar 를 남긴다
    - test -f "$BE_DEPLOY_PATH/app.jar" && cp "$BE_DEPLOY_PATH/app.jar" "$BE_DEPLOY_PATH/app.jar.prev" || true
    - cp "$BE_DIR/$BE_APP_MODULE"/build/libs/*.jar "$BE_DEPLOY_PATH/app.jar"
    - sudo systemctl restart "$BE_SERVICE"
    # 기동 대기 후 헬스체크 (실패 시 잡을 실패시킨다)
    - |
      for i in $(seq 1 30); do
        if curl -fsS http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"'; then
          echo "health OK"; exit 0
        fi
        sleep 2
      done
      echo "health check failed"; sudo journalctl -u "$BE_SERVICE" -n 50 --no-pager; exit 1
  environment:
    name: production/backend
    url: https://j15a403.p.ssafy.io/api
  rules:
    - if: '$CI_COMMIT_BRANCH == "master"'
      <<: *backend-changes
      when: manual # DB/시크릿 준비 전까지 수동 실행. 준비되면 이 줄을 지운다
```

배포 후 서버 모습:

```
/opt/j15a403/backend/
├── app.jar          # core-api bootJar (실행 대상)
└── app.jar.prev     # 직전 버전 (롤백용)
```

> `when: manual` 은 DB·시크릿이 준비될 때까지의 안전장치다. `/etc/j15a403/backend.env`
> 설정과 DB 기동이 끝나면 그 줄을 지워 자동 배포로 전환한다.

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
| 배포 후 `/actuator/health` 확인 | restart가 성공해도 기동에 실패할 수 있다. 헬스체크까지 봐야 배포 성공이라 할 수 있다 |
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
| `backend/**` | build | – | build | build → deploy(수동) |
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
| `docker: permission denied` | `sudo usermod -aG docker gitlab-runner` 후 runner 재시작 ([2-3](#2-3-docker--jre-설치-백엔드용)) |
| 빌드 산출물이 root 소유 | `docker run` 의 `-u "$(id -u):$(id -g)"` 누락 |
| `backend:deploy` 헬스체크 실패 | `sudo journalctl -u j15a403-backend -n 100`. 대개 `/etc/j15a403/backend.env` 의 DB 값 누락 |
| `sudo: no tty present` | `/etc/sudoers.d/gitlab-runner-backend` 의 NOPASSWD 규칙 확인 ([2-6](#2-6-백엔드-systemd-유닛)) |
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

- [x] `backend:build` — JDK 컨테이너에서 Gradle 빌드 + 테스트, bootJar artifacts
- [x] `backend:deploy` — jar 교체 + `systemctl restart` + 헬스체크 (현재 `when: manual`)
- [ ] 서버 준비: Docker / JRE 25 / systemd 유닛 / `/etc/j15a403/backend.env` ([2-3](#2-3-docker--jre-설치-백엔드용), [2-6](#2-6-백엔드-systemd-유닛))
- [ ] DB 기동 및 접속 정보 확정 → `backend:deploy` 의 `when: manual` 제거
- [ ] Nginx `/api/` 프록시 블록 활성화
- [ ] 무중단 배포 필요 여부 판단 (현재는 restart 중 수 초 다운타임)

### Dockerfile 로 갈 경우

컨테이너로 운영하기로 하면 멀티스테이지 Dockerfile(빌드 `temurin:25-jdk` → 런타임
`temurin:25-jre`)로 바꾼다. 그러면 호스트의 JRE와 systemd 유닛이 모두 필요 없어지고,
`backend:deploy` 는 `docker compose up -d --no-deps backend` 한 줄이 된다.
