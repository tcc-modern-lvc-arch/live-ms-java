# Build context = parent directory holding the sibling repos:
#   docker build -f live-ms-java/Dockerfile .
# ─── Stage 1: Build ───────────────────────────────────────────────────────────
# maven:3.9-eclipse-temurin-25
FROM maven:3.9-eclipse-temurin-25@sha256:93b8a14ea2f412782e4e842651273b4d903e35cc496284f178fbbe2d67d00976 AS build
WORKDIR /workspace

# Install proto-shared (local artifact, not on Maven Central)
COPY proto-shared/ ./proto-shared/
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -f proto-shared/pom.xml install -DskipTests -q

# POM-only layer — Maven dependency resolution cached until pom.xml changes
COPY live-ms-java/pom.xml ./live-ms-java/pom.xml
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -f live-ms-java/pom.xml dependency:go-offline -q || true

# Full build
COPY live-ms-java/src/ ./live-ms-java/src/
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -ntp -f live-ms-java/pom.xml clean package -DskipTests -q

# Split the fat JAR into layers (dependencies, spring-boot-loader,
# snapshot-dependencies, application) — Spring Boot's recommended CDS/AOT format
RUN java -Djarmode=tools -jar live-ms-java/target/live-ms-java.jar \
        extract --layers --destination /workspace/extracted

# ─── Stage 2: Runtime (+ AOT cache trained at image build) ────────────────────
# bellsoft/liberica-runtime-container:jre-25-cds-slim-musl
FROM bellsoft/liberica-runtime-container:jre-25-cds-slim-musl@sha256:5321bcfb1dc6a784368a017823cb7573f42d48bcedebf75b2eebdb4af773b0c6
WORKDIR /app

# Slowest → fastest changing: only 'application' changes on code edits
COPY --from=build /workspace/extracted/dependencies/          ./
COPY --from=build /workspace/extracted/spring-boot-loader/    ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/           ./

# JEP 483/514/515 AOT cache, trained with the same JDK as the runtime.
# Training boots the Spring context and exits right after refresh
# (spring.context.exit=onRefresh) with everything external switched off:
#   - Vault disabled (the "optional:vault://" import is skipped)
#   - gRPC channel to event-hub is lazy (never connects during training)
#   - polling schedulers pushed far out and the server port set to 0
# The JVM flags used here must match the ENTRYPOINT (GC / compact headers).
RUN java -XX:AOTCacheOutput=/app/app.aot \
         -XX:+UseCompactObjectHeaders -XX:+UseG1GC --enable-native-access=ALL-UNNAMED \
         -Dspring.context.exit=onRefresh \
         -Dspring.cloud.vault.enabled=false \
         -Dserver.port=0 \
         -Dais.polling.interval=86400000 \
         -Dcgesp.polling.interval=86400000 \
         -Dolhovivo.polling.interval=86400000 \
         -jar live-ms-java.jar \
    && test -s /app/app.aot

# Non-root user with fixed UID; app files stay root-owned (read-only for it)
RUN adduser -D -H -u 10001 -s /sbin/nologin app
USER 10001:10001

ENV JAVA_TOOL_OPTIONS="-XX:+UseCompactObjectHeaders -XX:+UseG1GC -XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError --enable-native-access=ALL-UNNAMED"

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["java", "-XX:AOTCache=/app/app.aot", "-jar", "/app/live-ms-java.jar"]
