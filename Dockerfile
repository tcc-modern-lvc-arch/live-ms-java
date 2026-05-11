# ─── Stage 1: Build ───────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace

# Install proto-shared (local artifact, not on Maven Central)
COPY proto-shared/ ./proto-shared/
RUN --mount=type=cache,target=/root/.m2 \
    mvn -f proto-shared/pom.xml install -DskipTests -q

# POM-only layer — Maven dependency resolution cached until any pom.xml changes
COPY live-ms-java/live-ms-java/pom.xml ./live-ms-java/pom.xml
RUN --mount=type=cache,target=/root/.m2 \
    mvn -f live-ms-java/pom.xml dependency:go-offline -q 2>/dev/null || true

# Full build
COPY live-ms-java/live-ms-java/ ./live-ms-java/
RUN --mount=type=cache,target=/root/.m2 \
    mvn -f live-ms-java/pom.xml clean package -DskipTests -q

# ─── Stage 2: Spring Boot layer extraction ────────────────────────────────────
FROM bellsoft/liberica-runtime-container:jre-25-cds-slim-musl AS layers
WORKDIR /app
COPY --from=build /workspace/live-ms-java/target/live-ms-java.jar app.jar
# Splits the fat JAR into four change-frequency buckets for Docker layer reuse
RUN java -Djarmode=layertools -jar app.jar extract

# ─── Stage 3: Runtime ─────────────────────────────────────────────────────────
FROM bellsoft/liberica-runtime-container:jre-25-cds-slim-musl
WORKDIR /app

# Layers ordered slowest→fastest changing: only 'application' is rebuilt on
# code changes; library and loader layers are reused from the Docker cache.
COPY --from=layers /app/dependencies/          ./
COPY --from=layers /app/spring-boot-loader/    ./
COPY --from=layers /app/snapshot-dependencies/ ./
COPY --from=layers /app/application/           ./

# CDS archive generated at first boot, persisted on a named volume across restarts
VOLUME ["/data"]

COPY live-ms-java/live-ms-java/entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

EXPOSE 8080

ENTRYPOINT ["/entrypoint.sh"]
