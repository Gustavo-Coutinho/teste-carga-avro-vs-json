package br.com.sandbox.kafka;

import br.com.sandbox.kafka.aplicacoes.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Aplicação principal que determina qual aplicação executar
 * baseado na variável de ambiente TIPO_APLICACAO
 */
public class AplicacaoPrincipal {
    private static final Logger logger = LoggerFactory.getLogger(AplicacaoPrincipal.class);

    public enum TipoAplicacao {
        PRODUTOR_AVRO,
        CONSUMIDOR_AVRO,
        PRODUTOR_JSON,
        CONSUMIDOR_JSON
    }

    public static void main(String[] args) {
        String tipoAplicacaoStr = System.getenv("TIPO_APLICACAO");

        if (tipoAplicacaoStr == null || tipoAplicacaoStr.isEmpty()) {
            logger.error("Variável de ambiente TIPO_APLICACAO não configurada!");
            logger.info("Valores válidos: PRODUTOR_AVRO, CONSUMIDOR_AVRO, PRODUTOR_JSON, CONSUMIDOR_JSON");
            System.exit(1);
        }

        try {
            TipoAplicacao tipoAplicacao = TipoAplicacao.valueOf(tipoAplicacaoStr.toUpperCase());
            logger.info("Iniciando aplicação: {}", tipoAplicacao);

            switch (tipoAplicacao) {
                case PRODUTOR_AVRO:
                    new ProdutorAvro().executar();
                    break;
                case CONSUMIDOR_AVRO:
                    new ConsumidorAvro().executar();
                    break;
                case PRODUTOR_JSON:
                    new ProdutorJson().executar();
                    break;
                case CONSUMIDOR_JSON:
                    new ConsumidorJson().executar();
                    break;
                default:
                    logger.error("Tipo de aplicação não reconhecido: {}", tipoAplicacao);
                    System.exit(1);
            }
        } catch (IllegalArgumentException e) {
            logger.error("Tipo de aplicação inválido: {}", tipoAplicacaoStr);
            logger.info("Valores válidos: PRODUTOR_AVRO, CONSUMIDOR_AVRO, PRODUTOR_JSON, CONSUMIDOR_JSON");
            System.exit(1);
        } catch (Exception e) {
            logger.error("Erro ao executar aplicação", e);
            System.exit(1);
        }
    }
}