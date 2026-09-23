# Stage 1: Build the application
FROM maven:3.9.6-eclipse-temurin-17-alpine AS build
WORKDIR /app
# Copy the pom.xml and download dependencies (caches this layer)
COPY pom.xml .
RUN mvn dependency:go-offline -B
# Copy source code and build
COPY src ./src
RUN mvn package -DskipTests

# Stage 2: Run the application
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
# Create a non-root user for security
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring
# Copy the built jar from the build stage
COPY --from=build /app/target/*.jar app.jar
# Expose the default port
EXPOSE 8091
# Run the jar
ENTRYPOINT ["java", "-jar", "app.jar"]
