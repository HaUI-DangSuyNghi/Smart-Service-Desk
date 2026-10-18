FROM maven:3.9.9-eclipse-temurin-17@sha256:82e47241881f23ad774f5db8829efca15758e8fdb5b1d64ea9f8d6420a85068e AS build
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
