FROM maven:3.9.16-eclipse-temurin-17 AS build
WORKDIR /app/backend-java
COPY backend-java/pom.xml .
RUN mvn dependency:go-offline -B
COPY backend-java/src ./src
RUN mvn package -Dmaven.test.skip=true

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/backend-java/target/*.jar app.jar
EXPOSE 9091
ENTRYPOINT ["java", "-jar", "app.jar"]
