FROM maven:3.9.9-eclipse-temurin-8 AS build
WORKDIR /app

COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:8-jre
WORKDIR /app

ENV SERVER_PORT=8080
COPY --from=build /app/target/*.jar /app/iiot-backend.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/iiot-backend.jar"]
