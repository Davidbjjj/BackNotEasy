FROM maven:3.8.5-openjdk-17-slim AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

FROM openjdk:17-jdk-slim
COPY target/BancoDeDados-0.0.1-SNAPSHOT.jar app.jar

# Define variáveis de ambiente recomendadas para o Render
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]
