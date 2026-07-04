# syntax=docker/dockerfile:1
FROM eclipse-temurin:26-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN --mount=type=cache,id=m2-repo,target=/root/.m2 chmod +x mvnw && ./mvnw dependency:go-offline -B
COPY src src
RUN --mount=type=cache,id=m2-repo,target=/root/.m2 ./mvnw package -DskipTests

FROM eclipse-temurin:26-jdk
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
