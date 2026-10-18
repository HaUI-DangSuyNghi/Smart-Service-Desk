FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /build/backend
COPY backend/pom.xml .
RUN mvn -B dependency:go-offline
COPY backend/src src
COPY contracts/intelligence/v1 /build/contracts/intelligence/v1
COPY docs/architecture/boundary-debt.json /build/docs/architecture/boundary-debt.json
RUN mvn -B verify
FROM eclipse-temurin:17.0.12_7-jre-jammy
WORKDIR /app
COPY --from=build /build/backend/target/dx-platform-0.1.0-SNAPSHOT.jar app.jar
RUN mkdir -p /var/lib/dx/files && chown 10001:10001 /var/lib/dx/files && chmod 700 /var/lib/dx/files
USER 10001:10001
ENTRYPOINT ["java", "-jar", "app.jar"]
