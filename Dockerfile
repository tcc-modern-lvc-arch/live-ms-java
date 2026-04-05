# Multi-stage build for the Live Adapter microservice
# Stage 1: Build
FROM maven:3.9.6-eclipse-temurin-25 AS builder

WORKDIR /build

# Copy POM
COPY pom.xml .

# Copy source code
COPY src/ src/

# Build the application
RUN mvn clean package -DskipTests

# Stage 2: Runtime
FROM amazoncorretto:25-alpine-jdk

WORKDIR /app

# Create non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Copy the built JAR from builder stage
COPY --from=builder /build/target/LVC*.jar app.jar

# Change ownership to appuser
RUN chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost:8080/api/v1/vessels/health || exit 1

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]

