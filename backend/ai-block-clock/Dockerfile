# 런타임 전용 이미지. jar 를 이미지 안에서 빌드하지 않고, 밖에서 빌드된 것을 받는다.
#
# ── 변경 이력 ─────────────────────────────────────────────────────────────────
# 2026-09-07 [S15P21A403-153] 멀티스테이지(builder + runtime) → 런타임 전용
#
#   변경 전:
#     FROM eclipse-temurin:25-jdk AS builder
#     COPY . .
#     RUN ./gradlew :core:core-api:bootJar --no-daemon
#     FROM eclipse-temurin:25-jre
#     COPY --from=builder /workspace/core/core-api/build/libs/*.jar app.jar
#
#   이유:
#     CI(backend:build)가 gradlew build 로 컴파일·테스트를 끝냈는데, builder
#     스테이지가 같은 소스를 다시 컴파일했다. 컴파일이 2회 도는 낭비이기도 하고,
#     더 중요하게는 테스트를 통과한 jar 가 아니라 재빌드한 다른 산출물이 배포됐다.
#     이제 테스트를 통과한 그 jar 를 그대로 이미지에 넣는다.
#
#   영향:
#     이미지를 빌드하기 전에 jar 가 있어야 한다. 없으면 COPY 단계에서 실패한다.
#       ./gradlew :core:core-api:bootJar
#     CI 에서는 backend:build 의 artifacts 가 이 경로에 내려온다.
#     .dockerignore 의 `**/build` 에 이 jar 만 예외를 뚫어뒀다.
# ─────────────────────────────────────────────────────────────────────────────

FROM eclipse-temurin:25-jre

# 실행 가능한 모듈은 core-api 하나뿐이다.
ARG JAR_FILE=core/core-api/build/libs/*.jar

WORKDIR /app

# compose.yaml 의 healthcheck 가 curl 을 쓴다.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY ${JAR_FILE} app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
