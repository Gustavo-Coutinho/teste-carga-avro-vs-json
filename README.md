# Aplicações de Teste de Carga Kafka - Avro vs JSON

Sistema de 4 aplicações Java leves para teste de performance no Confluent Cloud Kafka, comparando serialização Avro com JSON puro.

## 📋 Visão Geral

Este projeto contém 4 aplicações Java independentes que executam em containers Docker:

1. **Produtor Avro** - Produz 10 milhões de mensagens JSON de 2MB serializadas em Avro
2. **Consumidor Avro** - Consome as mensagens Avro
3. **Produtor JSON** - Produz 10 milhões de mensagens JSON de 2MB (sem Avro)
4. **Consumidor JSON** - Consome as mensagens JSON

Todas as aplicações coletam métricas de performance e enviam os resultados para tópicos específicos.

## 🏗️ Estrutura do Projeto

```
kafka-carga-teste/
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
│   │       ├── GeradorMensagemJson.java
│   │       └── MetricasDesempenho.java
│   └── resources/
│       └── avro/
│           └── MensagemCarga.avsc
├── pom.xml
├── Dockerfile
├── docker-compose.yml
├── .env (criar baseado em .env.template)
└── README.md
```

## 🚀 Como Usar

### Pré-requisitos

- Java 17+
- Maven 3.8+
- Docker e Docker Compose
- Conta no Confluent Cloud com cluster Kafka configurado
- API Keys do Confluent Cloud (Cluster e Schema Registry)

### 1. Configurar Variáveis de Ambiente

Crie o arquivo `.env` baseado no template `.env.template`:

```bash
cp .env.template .env
```

Edite o arquivo `.env` e preencha suas credenciais:

```bash
# Tipo de aplicação (PRODUTOR_AVRO, CONSUMIDOR_AVRO, PRODUTOR_JSON, CONSUMIDOR_JSON)
TIPO_APLICACAO=PRODUTOR_AVRO

# Confluent Cloud Kafka Cluster
KAFKA_BOOTSTRAP_SERVERS=pkc-xxxxx.us-east-1.aws.confluent.cloud:9092
KAFKA_CLUSTER_API_KEY=sua-api-key-cluster
KAFKA_CLUSTER_API_SECRET=seu-api-secret-cluster

# Confluent Cloud Schema Registry
SCHEMA_REGISTRY_URL=https://psrc-xxxxx.us-east-1.aws.confluent.cloud
SCHEMA_REGISTRY_API_KEY=sua-api-key-schema-registry
SCHEMA_REGISTRY_API_SECRET=seu-api-secret-schema-registry
```

### 2. Compilar o Projeto

```bash
mvn clean package
```

Isso irá:
- Gerar as classes Java a partir do schema Avro
- Compilar todas as aplicações
- Criar o JAR executável em `target/kafka-carga-teste-1.0.0.jar`

### 3. Construir a Imagem Docker

```bash
docker-compose build
```

### 4. Executar as Aplicações

#### Executar Produtor Avro

```bash
# Editar .env para definir TIPO_APLICACAO=PRODUTOR_AVRO
docker-compose up
```

#### Executar Consumidor Avro

```bash
# Editar .env para definir TIPO_APLICACAO=CONSUMIDOR_AVRO
docker-compose up
```

#### Executar Produtor JSON

```bash
# Editar .env para definir TIPO_APLICACAO=PRODUTOR_JSON
docker-compose up
```

#### Executar Consumidor JSON

```bash
# Editar .env para definir TIPO_APLICACAO=CONSUMIDOR_JSON
docker-compose up
```

## 📊 Tópicos Kafka

### Tópicos de Dados

- `carga-sandbox-avro` - Mensagens serializadas em Avro
- `carga-sandbox-json` - Mensagens em JSON puro

### Tópicos de Resultados

- `resultados-carga-sandbox-avro-producer` - Métricas do produtor Avro
- `resultados-carga-sandbox-avro-consumer` - Métricas do consumidor Avro
- `resultados-carga-sandbox-json-producer` - Métricas do produtor JSON
- `resultados-carga-sandbox-json-consumer` - Métricas do consumidor JSON

## 📈 Métricas Coletadas

Cada aplicação coleta e envia as seguintes métricas:

- **Total de mensagens** processadas
- **Total de bytes** transferidos (em MB)
- **Duração** da execução (em segundos)
- **Throughput** (mensagens/segundo e MB/segundo)
- **Latência média** (ms por mensagem)
- **Taxa de sucesso** (%)
- **Mensagens com erro**

Exemplo de saída de métricas:

```json
{
  "totalMensagens": 10000000,
  "mensagensSucesso": 10000000,
  "mensagensComErro": 0,
  "totalBytes": 20000000000,
  "totalMB": "19073.49",
  "duracaoSegundos": "1234.56",
  "throughputMensagensPorSegundo": "8100.45",
  "throughputMBPorSegundo": "15.45",
  "latenciaMediaMs": "0.12",
  "taxaSucessoPorcentagem": "100.00"
}
```

## 🔧 Configurações de Performance

As aplicações são otimizadas para throughput com as seguintes configurações:

### Producer
- `acks=1` - Confirmação do líder apenas
- `compression.type=lz4` - Compressão eficiente
- `batch.size=32768` - Lotes de 32KB
- `linger.ms=10` - Aguarda 10ms para formar lotes
- `buffer.memory=64MB` - Buffer de memória
- `max.request.size=3MB` - Suporta mensagens de até 3MB

### Consumer
- `max.poll.records=500` - Processa 500 registros por poll
- `fetch.min.bytes=1024` - Mínimo de 1KB por fetch
- `max.partition.fetch.bytes=3MB` - Suporta mensagens de até 3MB

## 🐳 Configurações Docker

### Limites de Recursos

```yaml
resources:
  limits:
    cpus: '2.0'
    memory: 2G
  reservations:
    cpus: '1.0'
    memory: 1G
```

### Variáveis JVM

```bash
JAVA_OPTS="-Xms512m -Xmx1536m -XX:+UseG1GC -XX:MaxGCPauseMillis=200"
```

## 📝 Schema Avro

O schema `MensagemCarga.avsc` define a estrutura das mensagens:

```json
{
  "type": "record",
  "name": "MensagemCarga",
  "namespace": "br.com.sandbox.kafka.avro",
  "fields": [
    {"name": "id", "type": "string"},
    {"name": "timestamp", "type": "long"},
    {"name": "sequencia", "type": "long"},
    {"name": "dados", "type": "string"},
    {"name": "versao", "type": "string", "default": "1.0"}
  ]
}
```

O campo `dados` contém um JSON string de aproximadamente 2MB com 10.000 registros.

## 🔒 Schema Registry

As aplicações Avro **criam automaticamente** o schema no Schema Registry do Confluent Cloud na primeira execução através da configuração:

```java
props.put(KafkaAvroSerializerConfig.AUTO_REGISTER_SCHEMAS, "true");
```

O schema é registrado com o subject `carga-sandbox-avro-value`.

## 🛠️ Solução de Problemas

### Erro de Autenticação

Verifique se as API Keys estão corretas no arquivo `.env`:
- `KAFKA_CLUSTER_API_KEY` e `KAFKA_CLUSTER_API_SECRET`
- `SCHEMA_REGISTRY_API_KEY` e `SCHEMA_REGISTRY_API_SECRET`

### Erro de Tamanho de Mensagem

Se as mensagens forem rejeitadas por tamanho, verifique:
1. Configuração do broker no Confluent Cloud
2. `max.request.size` no producer
3. `max.partition.fetch.bytes` no consumer

### Out of Memory

Ajuste as configurações JVM no `Dockerfile`:
```bash
JAVA_OPTS="-Xms512m -Xmx2048m -XX:+UseG1GC"
```

## 📚 Dependências Principais

- **Kafka Clients**: 3.6.1
- **Confluent Kafka Avro Serializer**: 7.6.0
- **Apache Avro**: 1.11.3
- **Gson**: 2.10.1
- **SLF4J**: 2.0.9

## 🧪 Ordem de Execução Recomendada

1. **Produtor Avro** → Gera mensagens Avro
2. **Consumidor Avro** → Consome mensagens Avro
3. **Produtor JSON** → Gera mensagens JSON
4. **Consumidor JSON** → Consome mensagens JSON

Aguarde cada aplicação finalizar antes de iniciar a próxima.

## 📊 Comparação Avro vs JSON

Após executar todas as 4 aplicações, compare as métricas nos tópicos de resultados para avaliar:

- **Throughput**: Mensagens/segundo e MB/segundo
- **Latência**: Tempo médio por mensagem
- **Tamanho**: Bytes totais transferidos
- **Performance**: Duração total da execução

## 🤝 Contribuições

Este projeto foi desenvolvido para testes de carga e comparação de performance entre Avro e JSON no Confluent Cloud Kafka.

## 📄 Licença

Projeto de uso interno para testes e benchmarking.
