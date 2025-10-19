package br.com.sandbox.kafka.util;

import br.com.sandbox.kafka.avro.MensagemCarga;
import br.com.sandbox.kafka.avro.Registro;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.*;

/**
 * Gera cargas estruturadas para comparação Avro vs JSON em tamanhos configuráveis.
 */
public class GeradorCargaEstruturada {
    private static final Random random = new Random();
    private static final Gson gson = new GsonBuilder().create();

    public static List<Registro> gerarRegistrosAvro(int quantidade) {
        List<Registro> lista = new ArrayList<>(quantidade);
        for (int i = 0; i < quantidade; i++) {
            Registro r = Registro.newBuilder()
                    .setIndice(i)
                    .setTexto(gerarTextoAleatorio(100))
                    .setNumero(random.nextDouble() * 1000)
                    .setTimestamp(System.currentTimeMillis())
                    .setUuid(UUID.randomUUID().toString())
                    .build();
            lista.add(r);
        }
        return lista;
    }

    public static Map<String, Object> gerarMapaRegistroJson(int quantidade) {
        List<Map<String, Object>> dados = new ArrayList<>(quantidade);
        for (int i = 0; i < quantidade; i++) {
            Map<String, Object> registro = new HashMap<>();
            registro.put("indice", i);
            registro.put("texto", gerarTextoAleatorio(100));
            registro.put("numero", random.nextDouble() * 1000);
            registro.put("timestamp", System.currentTimeMillis());
            registro.put("uuid", UUID.randomUUID().toString());
            dados.add(registro);
        }
        Map<String, Object> mensagem = new HashMap<>();
        mensagem.put("dados", dados);
        return mensagem;
    }

    public static String gerarJsonEstruturado(long sequencia) {
        int tamanhoKB = ConfiguracaoKafka.obterTamanhoMensagemKB();
        int registros = Math.max(1, (tamanhoKB * 1024) / 200);

        Map<String, Object> mensagem = new HashMap<>();
        mensagem.put("id", UUID.randomUUID().toString());
        mensagem.put("timestamp", System.currentTimeMillis());
        mensagem.put("sequencia", sequencia);
        mensagem.put("versao", "1.0");
        mensagem.putAll(gerarMapaRegistroJson(registros));
        return gson.toJson(mensagem);
    }

    public static List<Registro> gerarRegistrosTamanhoConfiguravel() {
        int tamanhoKB = ConfiguracaoKafka.obterTamanhoMensagemKB();
        int registros = Math.max(1, (tamanhoKB * 1024) / 200);
        return gerarRegistrosAvro(registros);
    }

    private static String gerarTextoAleatorio(int tamanho) {
        StringBuilder sb = new StringBuilder(tamanho);
        String caracteres = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        for (int i = 0; i < tamanho; i++) {
            sb.append(caracteres.charAt(random.nextInt(caracteres.length())));
        }
        return sb.toString();
    }
}
