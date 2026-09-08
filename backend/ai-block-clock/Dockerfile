# 두 가지 target 을 제공한다.
#
#   local (기본) — 컨테이너 안에서 소스를 컴파일한다. `docker compose up` 한 번으로 뜬다.
#   prod         — 밖에서 빌드된 jar 를 받는다. CI 가 테스트까지 끝낸 그 산출물을 쓴다.
#
# BuildKit 은 target 이 의존하는 스테이지만 빌드한다. 즉 `--target prod` 로 빌드하면
# builder 스테이지는 아예 실행되지 않는다.
#
# ── 변경 이력 ─────────────────────────────────────────────────────────────────
# 2026-09-07 [S15P21A403-153] 단일 멀티스테이지 → target 분리 (local / prod)
#
#   변경 전: builder 스테이지가 항상 소스를 컴파일하고, 그 jar 를 런타임에 복사했다.
#
#   이유:
#     CI(backend:build)가 gradlew build 로 컴파일·테스트를 끝냈는데 builder 스테이지가
#     같은 소스를 다시 컴파일했다. 컴파일이 2회 도는 낭비이기도 하고, gradlew bootJar
#     에는 test 태스크가 붙지 않아서(gradlew build 에만 붙는다) 테스트한 jar 와 배포된
#     jar 가 서로 다른 산출물이었다.
#
#     런타임 전용으로만 바꾸면 로컬에서 `docker compose up` 전에 bootJar 를 먼저
#     돌려야 해서 개발 편의가 나빠진다. 그래서 target 을 나눠 둘 다 만족시킨다.
#
#   영향:
#     compose.yaml      → target: local  (기존과 동일하게 동작)
#     compose.prod.yaml → target: prod   (CI artifacts 의 jar 사용)
#     .dockerignore 의 `**/build` 에 이 jar 만 예외를 뚫어뒀다 (prod target 용).
# ─────────────────────────────────────────────────────────────────────────────


# 두 target 이 공유하는 런타임 베이스
FROM eclipse-temurin:25-jre AS runtime-base

WORKDIR /app

# compose.yaml 의 healthcheck 가 curl 을 쓴다.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]


# 소스에서 컴파일하는 스테이지. local target 만 사용한다.
FROM eclipse-temurin:25-jdk AS builder

WORKDIR /workspace

COPY . .

RUN chmod +x gradlew
RUN ./gradlew :core:core-api:bootJar --no-daemon


# target: prod — 밖에서 빌드된 jar 를 받는다.
FROM runtime-base AS prod

# 실행 가능한 모듈은 core-api 하나뿐이다.
ARG JAR_FILE=core/core-api/build/libs/*.jar

COPY ${JAR_FILE} app.jar


# target: local — 컨테이너 안에서 빌드한 jar 를 쓴다.
# 마지막 스테이지이므로 --target 을 주지 않으면 이것이 기본값이 된다.
FROM runtime-base AS local

COPY --from=builder /workspace/core/core-api/build/libs/*.jar app.jar
