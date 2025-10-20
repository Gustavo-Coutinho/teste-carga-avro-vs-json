package br.com.sandbox.kafka.aplicacoes;

import br.com.sandbox.kafka.util.ConfiguracaoKafka;
import br.com.sandbox.kafka.util.MetricasDesempenho;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
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
    final long warmup = ConfiguracaoKafka.obterWarmupMensagens();
    final boolean transporte = ConfiguracaoKafka.isTransporteMode();
    final long totalAlvo = warmup + totalMensagensEsperadas;
        logger.info("Total de mensagens esperadas: {}", totalMensagensEsperadas);

    final int threads = ConfiguracaoKafka.obterConsumerThreads();
    Properties props = ConfiguracaoKafka.obterPropsConsumidor(false, GRUPO_CONSUMIDORES);
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
                final int idx = t;
                if (transporte) {
                    workers[t] = new Thread(() -> {
                        try (KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(props)) {
                            consumer.subscribe(Collections.singletonList(TOPICO), new ConsumerRebalanceListener() {
                                @Override
                                public void onPartitionsRevoked(java.util.Collection<TopicPartition> partitions) {
                                    // nada a fazer
                                }
                                @Override
                                public void onPartitionsAssigned(java.util.Collection<TopicPartition> partitions) {
                                    posicionarNoFimMenosN(consumer, totalAlvo, partitions);
                                }
                            });
                            // disparar ciclo de rebalance para obter assignment
                            consumer.poll(Duration.ofMillis(500));
                            while (executando.get() && mensagensProcessadas[0] < totalAlvo) {
                                ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(1000));
                                for (ConsumerRecord<String, byte[]> record : records) {
                                    try {
                                        long tamanho = record.serializedValueSize();
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
                    }, "consumidor-json-bytes-" + idx);
                } else {
                    workers[t] = new Thread(() -> {
                        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
                            consumer.subscribe(Collections.singletonList(TOPICO), new ConsumerRebalanceListener() {
                                @Override
                                public void onPartitionsRevoked(java.util.Collection<TopicPartition> partitions) { }
                                @Override
                                public void onPartitionsAssigned(java.util.Collection<TopicPartition> partitions) {
                                    posicionarNoFimMenosN(consumer, totalAlvo, partitions);
                                }
                            });
                            // disparar ciclo de rebalance para obter assignment
                            consumer.poll(Duration.ofMillis(500));
                            while (executando.get() && mensagensProcessadas[0] < totalAlvo) {
                                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(1000));
                                for (ConsumerRecord<String, String> record : records) {
                                    try {
                                        String mensagemJson = record.value();
                                        long tamanho = record.serializedValueSize();
                                        // parse leve com Gson para simetria com Avro decode
                                        br.com.sandbox.kafka.util.GeradorMensagemJson.deJson(mensagemJson);
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
                    }, "consumidor-json-" + idx);
                }
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

    /**
     * Posiciona o consumidor no offset de fim (latest) menos uma
     * quantidade alvo de mensagens distribuída pelas partições atribuídas.
     */
    private void posicionarNoFimMenosN(KafkaConsumer<?, ?> consumer, long totalAlvo) {
        try {
            // Garantir assignment
            int tentativas = 0;
            while (consumer.assignment().isEmpty() && tentativas < 30) {
                consumer.poll(Duration.ofMillis(200));
                tentativas++;
            }
            if (consumer.assignment().isEmpty()) {
                logger.warn("Não foi possível obter assignment para realizar seek inicial");
                return;
            }

            java.util.Set<TopicPartition> parts = consumer.assignment();
            posicionarNoFimMenosN(consumer, totalAlvo, parts);
        } catch (Exception e) {
            logger.warn("Falha ao posicionar offsets iniciais: {}", e.toString());
        }
    }

    private void posicionarNoFimMenosN(KafkaConsumer<?, ?> consumer, long totalAlvo, java.util.Collection<TopicPartition> parts) {
        try {
            java.util.Map<TopicPartition, Long> end = consumer.endOffsets(parts);
            java.util.Map<TopicPartition, Long> begin = consumer.beginningOffsets(parts);

            long porParticao = Math.max(1L, (long) Math.ceil((double) totalAlvo / Math.max(1, parts.size())));
            for (TopicPartition tp : parts) {
                long endOff = end.getOrDefault(tp, 0L);
                long beginOff = begin.getOrDefault(tp, 0L);
                long start = Math.max(beginOff, endOff - porParticao);
                consumer.seek(tp, start);
                logger.info("Thread reposicionada em {}:{} -> {} (fim {}, início {}), alvo/partição {}",
                        tp.topic(), tp.partition(), start, endOff, beginOff, porParticao);
            }
        } catch (Exception e) {
            logger.warn("Falha ao posicionar offsets iniciais: {}", e.toString());
        }
    }
}