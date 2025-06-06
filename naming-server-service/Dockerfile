# Use specific version for reproducible builds
FROM eclipse-temurin:17-jre-alpine@sha256:02c04793fa49ad5cd193c961403223755f9209a67894622e05438598b32f210e

# Set working directory
WORKDIR /app

# Add comprehensive metadata following OCI standards
LABEL maintainer="ConsultingFirm DevOps Team <devops@consultingfirm.com>"
LABEL org.opencontainers.image.title="naming-server-service"
LABEL org.opencontainers.image.description="Spring Cloud Netflix Eureka Naming Server for microservices discovery"
LABEL org.opencontainers.image.vendor="ConsultingFirm"
LABEL org.opencontainers.image.version="1.0.0"
LABEL org.opencontainers.image.source="https://github.com/consultingfirm/naming-server-service"

# Install security updates and create non-root user in single layer
RUN apk --no-cache upgrade && \
    addgroup -S appgroup && \
    adduser -S appuser -G appgroup && \
    # Create necessary directories with proper permissions
    mkdir -p /app/logs /app/tmp && \
    chown -R appuser:appgroup /app

# Set optimized JVM options for containerized environment
ENV JAVA_OPTS="-server \
    -XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:+UseG1GC \
    -XX:+UseStringDeduplication \
    -XX:+PrintGCDetails \
    -XX:+PrintGCTimeStamps \
    -Xloggc:/app/logs/gc.log \
    -XX:+UseGCLogFileRotation \
    -XX:NumberOfGCLogFiles=5 \
    -XX:GCLogFileSize=10M \
    -Djava.security.egd=file:/dev/./urandom \
    -Dspring.profiles.active=docker \
    -Dtmp.dir=/app/tmp"

# Copy JAR file with proper ownership
COPY --chown=appuser:appgroup target/naming_server_service-*.jar app.jar

# Switch to non-root user
USER appuser:appgroup

# Health check for container orchestration
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8761/actuator/health || exit 1

# Expose the application port
EXPOSE 8761

# Use exec form to ensure proper signal handling
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
