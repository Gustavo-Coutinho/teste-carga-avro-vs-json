package br.com.sandbox.kafka.aplicacoes;

import br.com.sandbox.kafka.util.ConfiguracaoKafka;
import br.com.sandbox.kafka.util.GeradorCargaEstruturada;
import br.com.sandbox.kafka.util.MetricasDesempenho;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Properties;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Produtor que envia 10 milhões de mensagens JSON (sem Avro)
 */
public class ProdutorJson {
    private static final Logger logger = LoggerFactory.getLogger(ProdutorJson.class);
    private static final String TOPICO_MENSAGENS = "carga-sandbox-json";
    private static final String TOPICO_RESULTADOS = "resultados-carga-sandbox-json-producer";
    private static final long INTERVALO_LOG = 100_000L;

    public void executar() {
        logger.info("Iniciando Produtor JSON");
        logger.info("Tópico de mensagens: {}", TOPICO_MENSAGENS);
        logger.info("Tópico de resultados: {}", TOPICO_RESULTADOS);
        
        final long totalMensagens = ConfiguracaoKafka.obterTotalMensagens();
        logger.info("Total de mensagens a enviar: {}", totalMensagens);

        Properties props = ConfiguracaoKafka.obterPropsProdutor(false);
    MetricasDesempenho metricas = new MetricasDesempenho();
        AtomicLong mensagensEnviadas = new AtomicLong(0);
    final long warmup = ConfiguracaoKafka.obterWarmupMensagens();

    try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {

            logger.info("Producer JSON criado com sucesso");

            // Enviar mensagens
            int numParticoes = ConfiguracaoKafka.obterNumParticoes();
            try {
                int metaParts = producer.partitionsFor(TOPICO_MENSAGENS).size();
                if (metaParts > 0) {
                    numParticoes = metaParts;
                }
            } catch (Exception e) {
                logger.warn("Não foi possível obter número de partições via metadata, usando valor de configuração: {}", numParticoes);
            }
            for (long i = 1; i <= totalMensagens; i++) {
                try {
                    // Gerar mensagem JSON estruturada (mesma estrutura do Avro)
                    String mensagemJson = GeradorCargaEstruturada.gerarJsonEstruturado(i);

                    String chave = "msg-" + i;
                    // Distribui explicitamente pelas partições 0..numParticoes-1
                    int particao = (int)((i - 1) % numParticoes);
                    ProducerRecord<String, String> record = 
                        new ProducerRecord<>(TOPICO_MENSAGENS, particao, chave, mensagemJson);

                    // Enviar de forma assíncrona
                    final long sequenciaAtual = i;
                    producer.send(record, (RecordMetadata metadata, Exception exception) -> {
                        if (exception == null) {
                            long count = mensagensEnviadas.incrementAndGet();
                            long serializedBytes = metadata.serializedValueSize();
                            if (count > warmup) {
                                metricas.registrarMensagem(serializedBytes, true);
                            }
                            if (count % INTERVALO_LOG == 0) {
                                logger.info("Progresso: {} mensagens enviadas ({} MB processados)", 
                                    count,
                                    String.format("%.2f", metricas.getTotalBytes() / (1024.0 * 1024.0)));
                            }
                        } else {
                            metricas.registrarMensagem(0, false);
                            logger.error("Erro ao enviar mensagem {}: {}", sequenciaAtual, exception.getMessage());
                        }
                    });

                    // Controle de fluxo
                    if (i % 1000 == 0) {
                        Thread.sleep(1);
                    }

                } catch (Exception e) {
                    logger.error("Erro ao processar mensagem {}", i, e);
                    metricas.registrarMensagem(0, false);
                }
            }

            // Aguardar envio de todas as mensagens
            logger.info("Aguardando conclusão do envio de todas as mensagens...");
            producer.flush();

            metricas.finalizar();
            logger.info("Envio concluído!");

            // Exibir relatório
            metricas.imprimirRelatorio();

            // Enviar métricas para tópico de resultados
            enviarMetricas(metricas);

        } catch (Exception e) {
            logger.error("Erro fatal no produtor", e);
            System.exit(1);
        }
    }

    private void enviarMetricas(MetricasDesempenho metricas) {
        logger.info("Enviando métricas para tópico: {}", TOPICO_RESULTADOS);

        Properties props = ConfiguracaoKafka.obterPropsProdutor(false);

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            String metricsJson = metricas.gerarRelatorioJson();
            ProducerRecord<String, String> record = 
                new ProducerRecord<>(TOPICO_RESULTADOS, "metricas-produtor-json", metricsJson);

            RecordMetadata metadata = producer.send(record).get();
            logger.info("Métricas enviadas com sucesso para partition {} offset {}", 
                metadata.partition(), metadata.offset());

        } catch (Exception e) {
            logger.error("Erro ao enviar métricas", e);
        }
    }
}