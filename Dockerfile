# ---- Build stage: compile the jar with Maven ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

# Download dependencies first so Docker can cache this layer
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q package -DskipTests

# ---- Run stage: small JRE image, non-root user ----
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /workspace/target/*.jar app.jar
USER app

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
