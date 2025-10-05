# -----------------------------
# STEP 1: Build the JAR with Maven
# -----------------------------
FROM maven:3.9.9-eclipse-temurin-17 AS builder

WORKDIR /app

# Copy pom.xml and download dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build the JAR
COPY src ./src
RUN mvn clean package -DskipTests

# -----------------------------
# STEP 2: Run the app with JDK
# -----------------------------
FROM openjdk:17-jdk-slim

WORKDIR /app

# Copy built JAR from builder
COPY --from=builder /app/target/OrderPaymentNotificationService-0.0.1-SNAPSHOT.jar app.jar

# Expose service port (default Spring Boot is 8080)
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
