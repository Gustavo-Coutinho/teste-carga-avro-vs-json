package br.com.sandbox.kafka.util;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig;
import org.slf4j.LoggerFactory;

import java.util.Properties;

/**
 * Utilitário para configuração do Kafka
 */
public class ConfiguracaoKafka {

    private static final String BOOTSTRAP_SERVERS = obterVariavelAmbiente("KAFKA_BOOTSTRAP_SERVERS");
    private static final String CLUSTER_API_KEY = obterVariavelAmbiente("KAFKA_CLUSTER_API_KEY");
    private static final String CLUSTER_API_SECRET = obterVariavelAmbiente("KAFKA_CLUSTER_API_SECRET");
    private static final String SCHEMA_REGISTRY_URL = obterVariavelAmbiente("SCHEMA_REGISTRY_URL");
    private static final String SCHEMA_REGISTRY_API_KEY = obterVariavelAmbiente("SCHEMA_REGISTRY_API_KEY");
    private static final String SCHEMA_REGISTRY_API_SECRET = obterVariavelAmbiente("SCHEMA_REGISTRY_API_SECRET");

    private static String obterVariavelAmbiente(String nome) {
        String valor = System.getenv(nome);
        if (valor == null || valor.isEmpty()) {
            throw new IllegalStateException("Variável de ambiente não configurada: " + nome);
        }
        return valor;
    }
    
    public static long obterTotalMensagens() {
        String valorStr = System.getenv("TOTAL_MENSAGENS");
        if (valorStr == null || valorStr.isEmpty()) {
            // Valor padrão caso a variável não esteja definida
            return 10_000_000L;
        }
        try {
            return Long.parseLong(valorStr);
        } catch (NumberFormatException e) {
            // Logar erro e retornar valor padrão
            LoggerFactory.getLogger(ConfiguracaoKafka.class).error("Erro ao converter TOTAL_MENSAGENS para long: {}", valorStr, e);
            return 10_000_000L;
        }
    }

    public static Properties obterPropsProdutor(boolean usarAvro) {
        Properties props = new Properties();

        // Configurações básicas
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        // Autenticação Confluent Cloud
        props.put("security.protocol", "SASL_SSL");
        props.put("sasl.mechanism", "PLAIN");
        props.put("sasl.jaas.config", String.format(
            "org.apache.kafka.common.security.plain.PlainLoginModule required username='%s' password='%s';",
            CLUSTER_API_KEY, CLUSTER_API_SECRET
        ));

        // Otimizações para throughput
        props.put(ProducerConfig.ACKS_CONFIG, "1");
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, "32768");
        props.put(ProducerConfig.LINGER_MS_CONFIG, "10");
        props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, "67108864");
        props.put(ProducerConfig.MAX_REQUEST_SIZE_CONFIG, "3145728"); // 3MB

        if (usarAvro) {
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class.getName());
            props.put(KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, SCHEMA_REGISTRY_URL);
            props.put("basic.auth.credentials.source", "USER_INFO");
            props.put("basic.auth.user.info", SCHEMA_REGISTRY_API_KEY + ":" + SCHEMA_REGISTRY_API_SECRET);
            props.put(KafkaAvroSerializerConfig.AUTO_REGISTER_SCHEMAS, "true");
        } else {
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        }

        return props;
    }

    public static Properties obterPropsConsumidor(boolean usarAvro, String grupoConsumidor) {
        Properties props = new Properties();

        // Configurações básicas
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, grupoConsumidor);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // Autenticação Confluent Cloud
        props.put("security.protocol", "SASL_SSL");
        props.put("sasl.mechanism", "PLAIN");
        props.put("sasl.jaas.config", String.format(
            "org.apache.kafka.common.security.plain.PlainLoginModule required username='%s' password='%s';",
            CLUSTER_API_KEY, CLUSTER_API_SECRET
        ));

        // Otimizações para throughput
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "500");
        props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, "1024");
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, "500");
        props.put(ConsumerConfig.MAX_PARTITION_FETCH_BYTES_CONFIG, "3145728"); // 3MB

        if (usarAvro) {
            props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class.getName());
            props.put(KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, SCHEMA_REGISTRY_URL);
            props.put("basic.auth.credentials.source", "USER_INFO");
            props.put("basic.auth.user.info", SCHEMA_REGISTRY_API_KEY + ":" + SCHEMA_REGISTRY_API_SECRET);
            props.put("specific.avro.reader", "true");
        } else {
            props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        }

        return props;
    }
}