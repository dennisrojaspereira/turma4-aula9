# Contexto de build: a RAIZ do repositorio (o pom filho depende do pom pai).
#   docker build -f monolith/Dockerfile -t techpix/monolith:local .
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY monolith/pom.xml monolith/pom.xml
COPY fraud-service/pom.xml fraud-service/pom.xml
RUN mvn -q -B -pl monolith -am dependency:go-offline
COPY monolith/src monolith/src
RUN mvn -q -B -pl monolith -am -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/monolith/target/monolith-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
