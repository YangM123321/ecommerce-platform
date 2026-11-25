# One Dockerfile for every module. docker-compose passes MODULE, e.g. MODULE=order-service.
#   docker build --build-arg MODULE=order-service -t order-service .

# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-17 AS build
ARG MODULE
WORKDIR /workspace
COPY . .
# -pl MODULE -am = build this module plus what it needs (e.g. common).
# The cache mount keeps ~/.m2 between builds, so 6 images don't each download the internet.
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl ${MODULE} -am package -DskipTests \
 && cp ${MODULE}/target/${MODULE}-*.jar /app.jar

# ---- Run stage: small JRE image, non-root user ----
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /app.jar app.jar
USER app
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
