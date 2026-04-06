# ─── Stage 1: Build ───────────────────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-25 AS builder

WORKDIR /build

# Cache deps layer — only invalidates on pom.xml change
COPY pom.xml .
RUN mvn dependency:go-offline -q

COPY src/ src/
RUN mvn clean package -DskipTests -o

# ─── Stage 2: Extracter ───────────────────────────────────────────────────────
# Boot app once with spring.context.exit=onRefresh to dump AppCDS archive.
# Redis connection is lazy — context refresh completes without Redis present.
FROM bellsoft/liberica-runtime-container:jre-25-cds-slim-glibc AS extracter

WORKDIR /app
COPY --from=builder /build/target/LVC*.jar app.jar

RUN java \
    -Dspring.context.exit=onRefresh \
    -XX:ArchiveClassesAtExit=application.jsa \
    -jar app.jar

# ─── Stage 3: Runtime ─────────────────────────────────────────────────────────
FROM bellsoft/liberica-runtime-container:jre-25-cds-slim-glibc

WORKDIR /app

RUN groupadd -r spring && useradd -r -g spring -s /sbin/nologin spring

COPY --from=builder /build/target/*.jar app.jar
COPY --from=extracter /app/application.jsa application.jsa

RUN chown -R spring:spring /app
USER spring

ENV JDK_JAVA_OPTIONS="\
  -XX:SharedArchiveFile=application.jsa \
  -XX:+UseG1GC \
  -XX:+UseContainerSupport \
  -XX:MaxRAMPercentage=75.0 \
  -XX:InitialRAMPercentage=50.0 \
  -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
