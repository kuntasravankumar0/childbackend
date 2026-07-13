# Multi-stage build for Spring Boot MDM Backend
# ⚠ Note: .mvn/ wrapper directory is NOT tracked in git, so we use a direct Maven install
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /app

# Install Maven directly (more reliable than depending on .mvn wrapper in git)
RUN apk add --no-cache maven

# Copy pom first for layer caching
COPY pom.xml ./
RUN mvn dependency:go-offline -q

# Copy source and build
COPY src ./src
RUN mvn package -DskipTests -q

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
