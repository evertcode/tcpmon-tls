# Stage 1: Build
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn package -DskipTests -q

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/target/tcpmon-tls-*.jar app.jar
VOLUME ["/app/sessions"]
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
CMD ["--ui-host", "0.0.0.0", "--sessions-dir", "/app/sessions"]
