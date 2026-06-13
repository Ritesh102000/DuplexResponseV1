FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21.0.5_11-jre
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
COPY --from=build /workspace/target/gateway-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --retries=12 CMD curl -fsS http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
