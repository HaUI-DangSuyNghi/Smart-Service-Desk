FROM maven:3.9.9-eclipse-temurin-17@sha256:f58d59b6273e785ac0a4477f6e9b5ba1d7731c75b906c0f7b34076f1851318cc AS build
WORKDIR /build
COPY backend/pom.xml .
RUN mvn -B dependency:go-offline
COPY backend/src src
COPY contracts/intelligence/v1 /contracts/intelligence/v1
RUN mvn -B verify
FROM eclipse-temurin:17.0.20.1_1-jre-jammy@sha256:b3054a20e1a851574cdb1692dd0db5310c647e62214a2a7abecbec1b9e3ae8f1
WORKDIR /app
COPY --from=build /build/target/dx-core-0.1.0-SNAPSHOT.jar app.jar
RUN mkdir -p /var/lib/dx/files && chown 10001:10001 /var/lib/dx/files && chmod 700 /var/lib/dx/files
USER 10001:10001
ENTRYPOINT ["java", "-jar", "app.jar"]
