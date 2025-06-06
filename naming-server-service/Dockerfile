FROM eclipse-temurin:17-jre-alpine

# Set working directory
WORKDIR /app

# Add metadata
LABEL maintainer="ConsultingFirm DevOps Team"
LABEL application="naming-server-service"
LABEL description="Spring Cloud Netflix Eureka Naming Server"

# Create non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Set environment variables
ENV JAVA_OPTS=""

# Copy the JAR file
COPY target/naming_server_service-*.jar app.jar

# Expose the application port
EXPOSE 8761

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]