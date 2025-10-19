package br.com.sandbox.kafka.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.*;
import br.com.sandbox.kafka.util.ConfiguracaoKafka;

/**
 * Utilitário para gerar mensagens JSON de tamanho configurável
 */
public class GeradorMensagemJson {
    private static final Gson gson = new GsonBuilder().create();
    private static final Random random = new Random();

    /**
     * Gera uma mensagem JSON com tamanho aproximado configurado via TAMANHO_MENSAGEM_KB
     * Substitui o método anterior gerarMensagem2MB para manter compatibilidade
     */
    public static String gerarMensagem2MB(long sequencia) {
        return gerarMensagemTamanhoConfiguravel(sequencia);
    }
    
    /**
     * Gera uma mensagem JSON com tamanho aproximado configurado via TAMANHO_MENSAGEM_KB
     */
    public static String gerarMensagemTamanhoConfiguravel(long sequencia) {
        Map<String, Object> mensagem = new HashMap<>();
        mensagem.put("id", UUID.randomUUID().toString());
        mensagem.put("timestamp", System.currentTimeMillis());
        mensagem.put("sequencia", sequencia);
        mensagem.put("versao", "1.0");

        // Obter tamanho configurado (em KB)
        int tamanhoKB = ConfiguracaoKafka.obterTamanhoMensagemKB();
        
        // Cada registro tem ~200 bytes
        // 1KB = 1024 bytes, então para X KB precisamos de X*1024/200 registros aproximadamente
        int quantidadeRegistros = (tamanhoKB * 1024) / 200;
        
        // Gerar dados para atingir o tamanho configurado
        List<Map<String, Object>> dados = new ArrayList<>();
        for (int i = 0; i < quantidadeRegistros; i++) {
            Map<String, Object> registro = new HashMap<>();
            registro.put("indice", i);
            registro.put("texto", gerarTextoAleatorio(100));
            registro.put("numero", random.nextDouble() * 1000);
            registro.put("timestamp", System.currentTimeMillis());
            registro.put("uuid", UUID.randomUUID().toString());
            dados.add(registro);
        }

        mensagem.put("dados", dados);

        return gson.toJson(mensagem);
    }

    /**
     * Gera texto aleatório de tamanho especificado
     */
    private static String gerarTextoAleatorio(int tamanho) {
        StringBuilder sb = new StringBuilder(tamanho);
        String caracteres = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

        for (int i = 0; i < tamanho; i++) {
            sb.append(caracteres.charAt(random.nextInt(caracteres.length())));
        }

        return sb.toString();
    }

    /**
     * Converte objeto para JSON
     */
    public static String paraJson(Object objeto) {
        return gson.toJson(objeto);
    }

    /**
     * Converte JSON para Map
     */
    public static Map<String, Object> deJson(String json) {
        return gson.fromJson(json, Map.class);
    }
}