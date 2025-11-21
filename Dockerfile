# ===============================
# Fase 1: Build do projeto
# ===============================
FROM maven:3.9-eclipse-temurin-17 AS builder

# Define o diretório de trabalho dentro do container
WORKDIR /app

# Define variáveis de ambiente para encoding UTF-8
ENV MAVEN_OPTS="-Dfile.encoding=UTF-8"

# Copia apenas o pom.xml primeiro (para aproveitar cache de dependências)
COPY pom.xml .

# Baixa as dependências antes de copiar o código-fonte (melhor uso de cache)
RUN mvn dependency:go-offline -B || true

# Agora copia o código-fonte
COPY src ./src

# Executa o build do projeto, ignorando os testes
# Adiciona flags para encoding UTF-8 e resolver problemas de caracteres especiais
RUN mvn clean package -DskipTests \
    -Dproject.build.sourceEncoding=UTF-8 \
    -Dproject.reporting.outputEncoding=UTF-8 \
    -Dmaven.compiler.encoding=UTF-8


# ===============================
# Fase 2: Runtime (execução)
# ===============================
FROM eclipse-temurin:17-jre-alpine

# Instala timezone data e define locale para UTF-8
RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/America/Sao_Paulo /etc/localtime && \
    echo "America/Sao_Paulo" > /etc/timezone

# Define o diretório de trabalho
WORKDIR /app

# Copia o .jar gerado na fase de build
COPY --from=builder /app/target/BancoDeDados-0.0.1-SNAPSHOT.jar app.jar

# Define variáveis de ambiente para otimização da JVM e encoding
ENV JAVA_OPTS="-Xmx512m -Xms256m -Dfile.encoding=UTF-8 -Duser.timezone=America/Sao_Paulo"

# Expõe a porta usada pela aplicação
EXPOSE 8080

# Comando de inicialização
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
