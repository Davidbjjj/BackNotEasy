# ===============================
# Fase 1: Build do projeto
# ===============================
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

# Copia pom para resolver dependências (cache)
COPY pom.xml ./

# Baixa dependências (falha controlada se tiver perfis não resolvidos)
RUN mvn -B dependency:go-offline || true

# Copia código
COPY src ./src

# Build (tests skip) - encoding já definido no pom
RUN mvn -B clean package -DskipTests


# ===============================
# Fase 2: Runtime
# ===============================
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Timezone
RUN apk add --no-cache tzdata && \
    cp /usr/share/zoneinfo/America/Sao_Paulo /etc/localtime && \
    echo "America/Sao_Paulo" > /etc/timezone

# Copia jar
COPY --from=builder /app/target/BancoDeDados-0.0.1-SNAPSHOT.jar app.jar

ENV JAVA_OPTS="-Xmx512m -Xms256m -Dfile.encoding=UTF-8 -Duser.timezone=America/Sao_Paulo"

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
