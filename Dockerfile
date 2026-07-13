# Multi-stage build for Spring Boot MDM Backend
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app

# Copy Maven wrapper + pom first (layer caching)
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw
RUN ./mvnw dependency:go-offline -q

# Copy source and build
COPY src ./src
RUN ./mvnw package -DskipTests -q

# ── Runtime image ──────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Create directories for file uploads
RUN mkdir -p /opt/mdm/files/apk

# Non-root user for security
RUN addgroup -S mdm && adduser -S mdm -G mdm
RUN chown -R mdm:mdm /opt/mdm
USER mdm

COPY --from=builder /app/target/mdm-backend-1.0.0.jar app.jar

EXPOSE 8080

ENV JAVA_OPTS="-Xmx512m -Xms256m"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
