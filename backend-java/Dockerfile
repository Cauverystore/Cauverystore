FROM maven:3.9.16-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml pom.xml
RUN mvn dependency:go-offline -B
COPY src/main ./src/main
RUN mvn package -Dmaven.test.skip=true

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
CMD ["java", "-jar", "app.jar"]
