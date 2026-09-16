FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY src src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S ccisj && adduser -S ccisj -G ccisj
COPY --from=build /workspace/target/*.jar app.jar
USER ccisj
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
