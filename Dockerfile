FROM maven:3.8.5-openjdk-17-slim AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Use uma imagem OpenJDK mais específica e compatível
FROM eclipse-temurin:17-jre-jammy

WORKDIR /app
COPY --from=builder /app/target/BancoDeDados-0.0.1-SNAPSHOT.jar app.jar

# Define variáveis de ambiente para otimização JVM
ENV JAVA_OPTS="-Xmx512m -Xms256m"

EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
