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

# JVM flags shared by the AOT training run, its validation and the container ENTRYPOINT.
# Declared once, before training, so all three always use exactly the same GC / heap /
# compact-headers settings (the AOT cache is only valid for matching flags).
ENV JAVA_TOOL_OPTIONS="-XX:+UseCompactObjectHeaders -XX:+UseG1GC -XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError --enable-native-access=ALL-UNNAMED"

# JEP 483/514/515 AOT cache, trained with the same JDK as the runtime.
# Training boots the Spring context and exits as soon as it is refreshed
# (spring.context.exit=onRefresh) — that exit is also what keeps the @Scheduled pollers from
# running in the build — with everything external switched off:
#   - Vault disabled (the "optional:vault://" import is skipped)
#   - gRPC channel to event-hub is lazy (never connects during training)
#   - server port 0, so nothing binds 8080
# The jar must be given with the SAME absolute path at training and at runtime: the cache
# records the classpath and rejects a mismatch.
RUN java -XX:AOTCacheOutput=/app/app.aot \
         -Dspring.context.exit=onRefresh \
         -Dspring.cloud.vault.enabled=false \
         -Dserver.port=0 \
         -jar /app/live-ms-java.jar \
    && test -s /app/app.aot

# Fail the build if the JVM would not accept the cache at runtime. -XX:AOTMode=on (unlike the
# default "auto") makes a rejected cache an error instead of a silent fallback to a slow start.
RUN java -XX:AOTMode=on -XX:AOTCache=/app/app.aot \
         -Dspring.context.exit=onRefresh \
         -Dspring.cloud.vault.enabled=false \
         -Dserver.port=0 \
         -jar /app/live-ms-java.jar

# Non-root user with fixed UID; app files stay root-owned (read-only for it)
RUN adduser -D -H -u 10001 -s /sbin/nologin app
USER 10001:10001

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health/readiness || exit 1

ENTRYPOINT ["java", "-XX:AOTCache=/app/app.aot", "-jar", "/app/live-ms-java.jar"]
