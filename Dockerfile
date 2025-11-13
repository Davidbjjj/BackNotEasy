# ===============================
# Fase 1: Build do projeto
# ===============================
FROM maven:3.8.5-openjdk-17-slim AS builder

# Define o diretório de trabalho dentro do container
WORKDIR /app

# Copia apenas o pom.xml primeiro (para aproveitar cache de dependências)
COPY pom.xml .

# Baixa as dependências antes de copiar o código-fonte (melhor uso de cache)
RUN mvn dependency:go-offline -B

# Agora copia o código-fonte
COPY src ./src

# Executa o build do projeto, ignorando os testes
RUN mvn clean package -DskipTests


# ===============================
# Fase 2: Runtime (execução)
# ===============================
FROM eclipse-temurin:17-jre-jammy

# Define o diretório de trabalho
WORKDIR /app

# Copia o .jar gerado na fase de build
COPY --from=builder /app/target/BancoDeDados-0.0.1-SNAPSHOT.jar app.jar

# Define variáveis de ambiente para otimização da JVM
ENV JAVA_OPTS="-Xmx512m -Xms256m"

# Expõe a porta usada pela aplicação
EXPOSE 8080

# Comando de inicialização
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
