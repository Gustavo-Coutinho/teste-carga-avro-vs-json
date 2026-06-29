# Teste de Carga Kafka: Avro vs. JSON com Benchmarking Avançado

> [!NOTE]
> Link da apresentação do TCC: https://youtu.be/HH218oy6rF0?si=cv8i10xucoJtxoPa


Este projeto contém um conjunto de aplicações Java para realizar testes de carga e benchmarks de performance no Confluent Cloud Kafka, comparando a serialização Avro com JSON. O sistema foi projetado para ser altamente configurável, permitindo testes em múltiplos cenários, incluindo diferentes tamanhos de mensagem, compressão, e modos de benchmark.

## 📋 Visão Geral

O projeto consiste em 4 aplicações Java containerizadas que operam em um tópico Kafka de 18 partições para maximizar a paralelização:

1.  **Produtor Avro**: Produz um número configurável de mensagens com um schema Avro estruturado.
2.  **Consumidor Avro**: Consome mensagens Avro de forma multi-threaded (um thread por partição) para alta performance.
3.  **Produtor JSON**: Produz o mesmo número de mensagens, mas em formato JSON.
4.  **Consumidor JSON**: Consome mensagens JSON, também em modo multi-threaded.

O principal objetivo é fornecer uma **plataforma justa e precisa para comparar Avro e JSON**, medindo throughput, latência e uso de CPU em diferentes condições.

## 🏗️ Estrutura do Projeto

```
teste-carga-avro-vs-json/
├── src/main/
│   ├── java/br/com/sandbox/kafka/
│   │   ├── AplicacaoPrincipal.java
│   │   ├── aplicacoes/
│   │   │   ├── ProdutorAvro.java
│   │   │   ├── ConsumidorAvro.java
│   │   │   ├── ProdutorJson.java
│   │   │   └── ConsumidorJson.java
│   │   └── util/
│   │       ├── ConfiguracaoKafka.java
│   │       ├── GeradorCargaEstruturada.java
│   │       └── MetricasDesempenho.java
│   └── resources/
│       └── avro/
│           └── MensagemCarga.avsc
├── pom.xml
├── Dockerfile
├── docker-compose.yml
├── .env (gerado a partir do .env.template)
├── .env.template
├── build-amd64.ps1 (Script de build para Windows)
├── setup.bat (Script de setup inicial para Windows)
└── README.md
```

## 🚀 Como Usar

### Pré-requisitos

*   Java 17+
*   Maven 3.8+
*   Docker e Docker Compose
*   Conta no Confluent Cloud com um cluster Kafka e Schema Registry.
*   API Keys do Confluent Cloud (Cluster e Schema Registry).

### 1. Setup Inicial

Execute o script de setup para criar seu arquivo `.env` a partir do template.

**No Windows:**
```powershell
.\setup.bat
```

### 2. Configurar Variáveis de Ambiente

Edite o arquivo `.env` recém-criado e preencha com suas credenciais do Confluent Cloud e as configurações do teste.

```bash
# =================================================
# CONFIGURAÇÃO DA APLICAÇÃO
# =================================================
# Tipo de aplicação a ser executada.
# Opções: PRODUTOR_AVRO, CONSUMIDOR_AVRO, PRODUTOR_JSON, CONSUMIDOR_JSON
TIPO_APLICACAO=PRODUTOR_AVRO

# =================================================
# CONFIGURAÇÃO DO BENCHMARK
# =================================================
# Modo de benchmark.
# E2E_PARSE: Mede o tempo de ponta a ponta, incluindo a desserialização/parsing do payload.
# TRANSPORTE: Mede apenas o tempo de transporte, ignorando o conteúdo da mensagem (usa ByteArrayDeserializer).
BENCH_MODE=E2E_PARSE

# Número total de mensagens a serem enviadas pelo produtor.
NUM_MENSAGENS=100000

# Tamanho alvo para cada mensagem em kilobytes (KB).
TAMANHO_MENSAGEM_KB=1024

# Número de mensagens a serem descartadas no início para o aquecimento (warm-up).
WARMUP_MENSAGENS=1000

# =================================================
# CONFIGURAÇÃO DO KAFKA
# =================================================
# Tipo de compressão para o produtor.
# Opções: none, gzip, snappy, lz4, zstd
COMPRESSION_TYPE=lz4

# Número de partições do tópico. Usado pelos produtores para distribuição e consumidores para paralelismo.
NUM_PARTICOES=18

# Número de threads para o consumidor (idealmente igual ao NUM_PARTICOES).
CONSUMER_THREADS=18

# Se o produtor Avro deve registrar o schema automaticamente.
AUTO_REGISTER_SCHEMAS=true

# =================================================
# CREDENCIAIS DO CONFLUENT CLOUD
# =================================================
# Confluent Cloud Kafka Cluster
KAFKA_BOOTSTRAP_SERVERS=pkc-xxxxx.us-east-1.aws.confluent.cloud:9092
KAFKA_CLUSTER_API_KEY=sua-api-key-cluster
KAFKA_CLUSTER_API_SECRET=seu-api-secret-cluster

# Confluent Cloud Schema Registry
SCHEMA_REGISTRY_URL=https://psrc-xxxxx.us-east-1.aws.confluent.cloud
SCHEMA_REGISTRY_API_KEY=sua-api-key-schema-registry
SCHEMA_REGISTRY_API_SECRET=seu-api-secret-schema-registry
```

### 3. Construir a Imagem Docker

Use o script de build para compilar o projeto com Maven e construir a imagem Docker.

**No Windows (arquitetura amd64):**
```powershell
.\build-amd64.ps1
```

### 4. Executar os Testes

Para cada teste, configure a variável `TIPO_APLICACAO` no arquivo `.env` e execute o `docker-compose`.

**Exemplo: Executar o Produtor Avro**
1.  Edite `.env` e defina `TIPO_APLICACAO=PRODUTOR_AVRO`.
2.  Execute o container:
    ```bash
    docker-compose up
    ```
3.  Aguarde a finalização. O container irá parar automaticamente.

Repita o processo para `CONSUMIDOR_AVRO`, `PRODUTOR_JSON` e `CONSUMIDOR_JSON`.

## 📊 Tópicos Kafka

*   **Tópicos de Dados**: `carga-sandbox-avro` e `carga-sandbox-json`.
*   **Tópicos de Resultados**: `resultados-carga-sandbox-avro-producer`, `resultados-carga-sandbox-avro-consumer`, etc.

## 📈 Métricas Coletadas

As métricas são publicadas em um tópico de resultados em formato JSON.

Exemplo de saída (`BENCH_MODE=E2E_PARSE`):
```json
{
  "aplicacao": "PRODUTOR_AVRO",
  "compressionType": "lz4",
  "benchMode": "E2E_PARSE",
  "tamanhoMensagemKB": 1024,
  "totalMensagens": 100000,
  "mensagensSucesso": 99000,
  "mensagensComErro": 0,
  "totalBytes": 101376000,
  "totalMB": "96.68",
  "duracaoSegundos": "15.83",
  "throughputMensagensPorSegundo": "6253.95",
  "throughputMBPorSegundo": "6.11",
  "tempoPorMensagemMs": "0.16",
  "taxaSucessoPorcentagem": "100.00"
}
```
**Nota**: `tempoPorMensagemMs` representa o tempo médio de processamento por mensagem: (`duração / nº de mensagens`)

## 🔬 Metodologia de Benchmark: Avro vs. JSON

Para uma comparação justa, o sistema implementa duas estratégias de teste controladas pela variável `BENCH_MODE`.

### Modo 1: `E2E_PARSE` (Ponta-a-Ponta com Parsing)

Este é o modo de teste mais realista. Ele mede a performance completa do ciclo de vida da mensagem:
*   **Produtor**: Serializa um objeto Java estruturado para Avro ou JSON.
*   **Consumidor**: Desserializa o payload de volta para um objeto Java.

Neste modo, tanto o consumidor Avro quanto o JSON realizam trabalho de parsing, garantindo uma comparação simétrica do custo de CPU e da eficiência da serialização.

### Modo 2: `TRANSPORTE` (Apenas Transporte)

Este modo foca exclusivamente na eficiência do transporte (I/O de rede, compressão, overhead do broker).
*   **Produtor**: Serializa a mensagem normalmente.
*   **Consumidor**: Recebe as mensagens como um array de bytes (`byte[]`) e **não faz parsing** do conteúdo.

Este modo é útil para isolar o impacto do tamanho do payload e da compressão na performance da rede, removendo a sobrecarga da desserialização da análise.

## 📝 Schema Avro Estruturado

O schema Avro foi refatorado para ser totalmente estruturado, abandonando a abordagem anterior de encapsular um JSON. Isso permite que o Avro utilize todo o seu potencial de serialização binária e compactação.

`src/main/resources/avro/MensagemCarga.avsc`:
```json
{
  "type": "record",
  "name": "MensagemCarga",
  "namespace": "br.com.sandbox.kafka.avro",
  "fields": [
    {"name": "id", "type": "string"},
    {"name": "timestamp", "type": "long"},
    {"name": "sequencia", "type": "long"},
    {
      "name": "dados",
      "type": {
        "type": "array",
        "items": {
          "name": "Registro",
          "type": "record",
          "fields": [
            {"name": "campo1", "type": "string"},
            {"name": "campo2", "type": "string"},
            {"name": "campo3", "type": "long"},
            {"name": "campo4", "type": "double"},
            {"name": "campo5", "type": "boolean"}
          ]
        }
      }
    },
    {"name": "versao", "type": "string", "default": "1.0"}
  ]
}
```

## 🛠️ Solução de Problemas

*   **Erro de Autenticação**: Verifique todas as API keys no arquivo `.env`.
*   **Erro de Tamanho de Mensagem**: Verifique a configuração `message.max.bytes` no seu tópico do Confluent Cloud.
*   **Out of Memory**: Ajuste as configurações de memória da JVM (`JAVA_OPTS`) no `docker-compose.yml`.

## 📚 Dependências Principais

*   **Kafka Clients**: 3.6.1
*   **Confluent Kafka Avro Serializer**: 7.6.0
*   **Apache Avro**: 1.11.3
*   **Gson**: 2.10.1
*   **SLF4J**: 2.0.9
