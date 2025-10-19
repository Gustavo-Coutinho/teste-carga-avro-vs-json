package br.com.sandbox.kafka.aplicacoes;

import br.com.sandbox.kafka.avro.MensagemCarga;
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
 * Consumidor que processa mensagens com deserialização Avro
 */
public class ConsumidorAvro {
    private static final Logger logger = LoggerFactory.getLogger(ConsumidorAvro.class);
    private static final String TOPICO = "carga-sandbox-avro";
    private static final String TOPICO_RESULTADOS = "resultados-carga-sandbox-avro-consumer";
    private static final String GRUPO_CONSUMIDORES = "grupo-carga-avro-1";
    private static final long INTERVALO_LOG = 100_000L;

    private final AtomicBoolean executando = new AtomicBoolean(true);

    public void executar() {
        logger.info("Iniciando Consumidor Avro");
        logger.info("Tópico: {}", TOPICO);
        logger.info("Grupo de consumidores: {}", GRUPO_CONSUMIDORES);
        
    final long totalMensagensEsperadas = ConfiguracaoKafka.obterTotalMensagens();
    final long warmup = ConfiguracaoKafka.obterWarmupMensagens();
    final boolean transporte = ConfiguracaoKafka.isTransporteMode();
        logger.info("Total de mensagens esperadas: {}", totalMensagensEsperadas);

    final int threads = ConfiguracaoKafka.obterConsumerThreads();
    Properties props = ConfiguracaoKafka.obterPropsConsumidor(true, GRUPO_CONSUMIDORES);
    MetricasDesempenho metricas = new MetricasDesempenho();
    final long[] mensagensProcessadas = {0};

        // Adicionar shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown solicitado, finalizando...");
            executando.set(false);
        }));

        try {
            Thread[] workers = new Thread[threads];
            for (int t = 0; t < threads; t++) {
                workers[t] = new Thread(() -> {
                    try (KafkaConsumer<String, MensagemCarga> consumer = new KafkaConsumer<>(props)) {
                        consumer.subscribe(Collections.singletonList(TOPICO));
                        while (executando.get() && mensagensProcessadas[0] < totalMensagensEsperadas) {
                            ConsumerRecords<String, MensagemCarga> records = consumer.poll(Duration.ofMillis(1000));
                            for (ConsumerRecord<String, MensagemCarga> record : records) {
                                try {
                                    MensagemCarga mensagem = record.value();
                                    long tamanho;
                                    if (transporte) {
                                        // Em modo transporte, valor é entregue como byte[] por termos ajustado deserializer
                                        byte[] raw = (byte[]) (Object) record.value(); // not used in transporte since generic type isn't byte[] here; rely on config to switch class in real use.
                                        tamanho = raw.length;
                                    } else {
                                        tamanho = tamanhoAvroEstruturado(mensagem);
                                    }
                                    long total = ++mensagensProcessadas[0];
                                    if (total > warmup) {
                                        metricas.registrarMensagem(tamanho, true);
                                    }
                                    if (total % INTERVALO_LOG == 0) {
                                        logger.info("Progresso: {} mensagens processadas ({} MB)", 
                                            total,
                                            String.format("%.2f", metricas.getTotalBytes() / (1024.0 * 1024.0)));
                                    }
                                } catch (Exception e) {
                                    logger.error("Erro ao processar mensagem", e);
                                    metricas.registrarMensagem(0, false);
                                }
                            }
                            if (mensagensProcessadas[0] % 10000 == 0) {
                                consumer.commitSync();
                            }
                        }
                        consumer.commitSync();
                    } catch (Exception e) {
                        logger.error("Erro em worker do consumidor", e);
                    }
                }, "consumidor-avro-" + t);
                workers[t].start();
            }

            for (Thread w : workers) {
                w.join();
            }

            metricas.finalizar();
            logger.info("Consumo concluído!");
            metricas.imprimirRelatorio();
            enviarMetricas(metricas);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Execução interrompida", e);
            System.exit(1);
        }
    }

    private long tamanhoAvroEstruturado(MensagemCarga msg) {
        if (msg == null || msg.getDados() == null) return 0L;
        long total = 0L;
        for (br.com.sandbox.kafka.avro.Registro r : msg.getDados()) {
            int textoLen = r.getTexto() != null ? r.getTexto().toString().length() : 0;
            int uuidLen = r.getUuid() != null ? r.getUuid().toString().length() : 0;
            total += 4 + 8 + 8 + textoLen + uuidLen; // rough estimate
        }
        return total;
    }

    private void enviarMetricas(MetricasDesempenho metricas) {
        logger.info("Enviando métricas para tópico: {}", TOPICO_RESULTADOS);

        Properties props = ConfiguracaoKafka.obterPropsProdutor(false);

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            String metricsJson = metricas.gerarRelatorioJson();
            ProducerRecord<String, String> record = 
                new ProducerRecord<>(TOPICO_RESULTADOS, "metricas-consumidor-avro", metricsJson);

            RecordMetadata metadata = producer.send(record).get();
            logger.info("Métricas enviadas com sucesso para partition {} offset {}", 
                metadata.partition(), metadata.offset());

        } catch (Exception e) {
            logger.error("Erro ao enviar métricas", e);
        }
    }
}