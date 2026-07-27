FROM maven:3.9.16-eclipse-temurin-17 AS build
WORKDIR /app
COPY . .
RUN cd backend-java && mvn package -Dmaven.test.skip=true

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/backend-java/target/*.jar app.jar
CMD ["java", "-jar", "app.jar"]
