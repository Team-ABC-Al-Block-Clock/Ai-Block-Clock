FROM eclipse-temurin:25-jdk AS builder

WORKDIR /workspace

COPY . .

RUN chmod +x gradlew
RUN ./gradlew :core:core-api:bootJar --no-daemon


FROM eclipse-temurin:25-jre

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=builder /workspace/core/core-api/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]