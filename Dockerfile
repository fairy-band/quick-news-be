# Build stage
FROM gradle:8.5-jdk21 AS build
WORKDIR /app

# Copy gradle files
COPY gradle gradle
COPY gradlew .
COPY settings.gradle.kts .
COPY build.gradle.kts .

# Copy version catalog
COPY gradle/libs.version.toml gradle/

# Copy source code
COPY . .

# Build the application
RUN ./gradlew :api:bootJar :batch:bootJar :admin:bootJar --no-daemon

# Runtime stage for API
FROM azul/zulu-openjdk-alpine:21-jre AS api
WORKDIR /app

# Copy the built jar
COPY --from=build /app/api/build/libs/*.jar app.jar

# Expose port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]

# Runtime stage for Batch
FROM azul/zulu-openjdk-alpine:21-jre AS batch
WORKDIR /app

# Copy the built jar
COPY --from=build /app/batch/build/libs/*.jar app.jar

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]

# Runtime stage for Admin
FROM azul/zulu-openjdk-alpine:21-jre AS admin
WORKDIR /app

# Copy the built jar
COPY --from=build /app/admin/build/libs/*.jar app.jar

# Expose port
EXPOSE 8083

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
