package br.com.sandbox.kafka.aplicacoes;

import br.com.sandbox.kafka.util.ConfiguracaoKafka;
import br.com.sandbox.kafka.util.MetricasDesempenho;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Consumidor que processa mensagens JSON (sem Avro)
 */
public class ConsumidorJson {
    private static final Logger logger = LoggerFactory.getLogger(ConsumidorJson.class);
    private static final String TOPICO = "carga-sandbox-json";
    private static final String TOPICO_RESULTADOS = "resultados-carga-sandbox-json-consumer";
    private static final String GRUPO_CONSUMIDORES = "grupo-carga-json-1";
    private static final long INTERVALO_LOG = 100_000L;

    private final AtomicBoolean executando = new AtomicBoolean(true);

    public void executar() {
        logger.info("Iniciando Consumidor JSON");
        logger.info("Tópico: {}", TOPICO);
        logger.info("Grupo de consumidores: {}", GRUPO_CONSUMIDORES);
        
        final long totalMensagensEsperadas = ConfiguracaoKafka.obterTotalMensagens();
        logger.info("Total de mensagens esperadas: {}", totalMensagensEsperadas);

        Properties props = ConfiguracaoKafka.obterPropsConsumidor(false, GRUPO_CONSUMIDORES);
        MetricasDesempenho metricas = new MetricasDesempenho();
        long mensagensProcessadas = 0;

        // Adicionar shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown solicitado, finalizando...");
            executando.set(false);
        }));

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {

            consumer.subscribe(Collections.singletonList(TOPICO));
            logger.info("Consumer JSON inscrito no tópico {}", TOPICO);

            while (executando.get() && mensagensProcessadas < totalMensagensEsperadas) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));

                for (ConsumerRecord<String, String> record : records) {
                    try {
                        String mensagemJson = record.value();

                        // Processar mensagem JSON
                        long tamanho = mensagemJson.getBytes().length;
                        metricas.registrarMensagem(tamanho, true);
                        mensagensProcessadas++;

                        if (mensagensProcessadas % INTERVALO_LOG == 0) {
                            logger.info("Progresso: {} mensagens processadas ({} MB)", 
                                mensagensProcessadas,
                                String.format("%.2f", metricas.getTotalBytes() / (1024.0 * 1024.0)));
                        }

                    } catch (Exception e) {
                        logger.error("Erro ao processar mensagem", e);
                        metricas.registrarMensagem(0, false);
                    }
                }

                // Commit periódico
                if (mensagensProcessadas % 10000 == 0) {
                    consumer.commitSync();
                }
            }

            consumer.commitSync();
            metricas.finalizar();

            logger.info("Consumo concluído!");
            metricas.imprimirRelatorio();

            // Enviar métricas
            enviarMetricas(metricas);

        } catch (Exception e) {
            logger.error("Erro fatal no consumidor", e);
            System.exit(1);
        }
    }

    private void enviarMetricas(MetricasDesempenho metricas) {
        logger.info("Enviando métricas para tópico: {}", TOPICO_RESULTADOS);

        Properties props = ConfiguracaoKafka.obterPropsProdutor(false);

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            String metricsJson = metricas.gerarRelatorioJson();
            ProducerRecord<String, String> record = 
                new ProducerRecord<>(TOPICO_RESULTADOS, "metricas-consumidor-json", metricsJson);

            RecordMetadata metadata = producer.send(record).get();
            logger.info("Métricas enviadas com sucesso para partition {} offset {}", 
                metadata.partition(), metadata.offset());

        } catch (Exception e) {
            logger.error("Erro ao enviar métricas", e);
        }
    }
}