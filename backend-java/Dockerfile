FROM maven:3.9.16-eclipse-temurin-17 AS build
WORKDIR /app
COPY backend-java/pom.xml ./backend-java/pom.xml
RUN mvn dependency:go-offline -B -f backend-java/pom.xml
COPY backend-java/src/main ./backend-java/src/main
RUN mvn package -Dmaven.test.skip=true -f backend-java/pom.xml

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/backend-java/target/*.jar app.jar
EXPOSE 9091
ENTRYPOINT ["java", "-jar", "app.jar"]
