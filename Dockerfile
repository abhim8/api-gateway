# Stage 1: Build the application
FROM eclipse-temurin:21-jdk AS builder
WORKDIR /build
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw -B dependency:go-offline
COPY src src
RUN ./mvnw -B package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:21-jre AS runtime
RUN groupadd -r gateway && useradd -r -g gateway gateway
USER gateway
WORKDIR /app
COPY --from=builder /build/target/*.jar app.jar
EXPOSE 8000
ENTRYPOINT ["java", "-jar", "app.jar"]
