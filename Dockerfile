# syntax=docker/dockerfile:1

FROM maven:3.9.11-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Resolve dependencies separately so Docker can reuse this layer when only source
# files change.
COPY pom.xml ./
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B clean package -DskipTests \
    && cp target/*.jar /app/app.jar

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S spring \
    && adduser -S spring -G spring

COPY --from=build --chown=spring:spring /app/app.jar ./app.jar

USER spring:spring
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]