# Stage 1: Build
FROM eclipse-temurin:24-jdk-alpine AS builder

WORKDIR /app

# Gradle wrapper + build files (dependency layer cache)
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle/ gradle/
COPY stayops-mock-ota/build.gradle.kts stayops-mock-ota/build.gradle.kts

RUN chmod +x gradlew && ./gradlew dependencies --no-daemon

# Source code
COPY src/ src/

RUN ./gradlew :bootJar -x test --no-daemon

# Stage 2: Runtime
FROM eclipse-temurin:24-jre-alpine

RUN addgroup -S app && adduser -S app -G app

WORKDIR /app

COPY --from=builder /app/build/libs/stayops-0.0.1-SNAPSHOT.jar app.jar

RUN chown -R app:app /app
USER app

EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
