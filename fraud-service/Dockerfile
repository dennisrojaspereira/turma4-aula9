# Contexto de build: a RAIZ do repositorio (o pom filho depende do pom pai).
#   docker build -f fraud-service/Dockerfile -t techpix/fraud-service:local .
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
# Primeiro so os poms: a camada de dependencias e cacheada enquanto os poms nao mudarem.
COPY pom.xml .
COPY monolith/pom.xml monolith/pom.xml
COPY fraud-service/pom.xml fraud-service/pom.xml
RUN mvn -q -B -pl fraud-service -am dependency:go-offline
COPY fraud-service/src fraud-service/src
RUN mvn -q -B -pl fraud-service -am -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/fraud-service/target/fraud-service-*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
