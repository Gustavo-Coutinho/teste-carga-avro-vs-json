FROM eclipse-temurin:17-jre

LABEL maintainer="sandbox"
LABEL description="Aplicação de teste de carga Kafka"

WORKDIR /app

# Copiar o JAR da aplicação
COPY ./target/*.jar /app/aplicacao.jar

# Variáveis de ambiente padrão
ENV JAVA_OPTS="-Xms512m -Xmx1536m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# Expor porta para métricas JMX (opcional)
EXPOSE 9999

# Script de entrada
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/aplicacao.jar"]