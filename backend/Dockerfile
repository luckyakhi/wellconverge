# --- Build stage: uses a Gradle image so no committed wrapper jar is required ---
FROM gradle:8.10-jdk21 AS build
WORKDIR /workspace
# Copy build config first for better layer caching
COPY settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle
COPY membership ./membership
COPY bootstrap ./bootstrap
RUN gradle --no-daemon :bootstrap:bootJar

# --- Runtime stage ---
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app app
COPY --from=build /workspace/bootstrap/build/libs/wellconverge-backend.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
