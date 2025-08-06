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

# Build the application with layered JARs
RUN ./gradlew :api:bootJar :batch:bootJar :admin:bootJar --no-daemon

# Extract layered JARs for better Docker layer caching
# Extract API layered JAR
WORKDIR /app/api/build/libs
RUN java -Djarmode=layertools -jar *.jar extract

# Extract Batch layered JAR  
WORKDIR /app/batch/build/libs
RUN java -Djarmode=layertools -jar *.jar extract

# Extract Admin layered JAR
WORKDIR /app/admin/build/libs
RUN java -Djarmode=layertools -jar *.jar extract

# Runtime stage for API
FROM azul/zulu-openjdk-alpine:21-jre AS api
WORKDIR /app

# Copy layers in order for optimal caching
COPY --from=build /app/api/build/libs/dependencies/ ./
COPY --from=build /app/api/build/libs/spring-boot-loader/ ./
COPY --from=build /app/api/build/libs/snapshot-dependencies/ ./
COPY --from=build /app/api/build/libs/application/ ./

# Expose port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]

# Runtime stage for Batch
FROM azul/zulu-openjdk-alpine:21-jre AS batch
WORKDIR /app

# Copy layers in order for optimal caching
COPY --from=build /app/batch/build/libs/dependencies/ ./
COPY --from=build /app/batch/build/libs/spring-boot-loader/ ./
COPY --from=build /app/batch/build/libs/snapshot-dependencies/ ./
COPY --from=build /app/batch/build/libs/application/ ./

# Expose port
EXPOSE 8082

# Run the application
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]

# Runtime stage for Admin
FROM azul/zulu-openjdk-alpine:21-jre AS admin
WORKDIR /app

# Copy layers in order for optimal caching
COPY --from=build /app/admin/build/libs/dependencies/ ./
COPY --from=build /app/admin/build/libs/spring-boot-loader/ ./
COPY --from=build /app/admin/build/libs/snapshot-dependencies/ ./
COPY --from=build /app/admin/build/libs/application/ ./

# Expose port
EXPOSE 8083

# Run the application
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
