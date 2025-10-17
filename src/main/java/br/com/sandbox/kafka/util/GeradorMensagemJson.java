package br.com.sandbox.kafka.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.*;

/**
 * Utilitário para gerar mensagens JSON de ~2MB
 */
public class GeradorMensagemJson {
    private static final Gson gson = new GsonBuilder().create();
    private static final Random random = new Random();

    /**
     * Gera uma mensagem JSON de aproximadamente 2MB
     */
    public static String gerarMensagem2MB(long sequencia) {
        Map<String, Object> mensagem = new HashMap<>();
        mensagem.put("id", UUID.randomUUID().toString());
        mensagem.put("timestamp", System.currentTimeMillis());
        mensagem.put("sequencia", sequencia);
        mensagem.put("versao", "1.0");

        // Gerar dados para atingir ~2MB
        // Cada registro tem ~200 bytes, precisamos de ~10.000 registros
        List<Map<String, Object>> dados = new ArrayList<>();
        for (int i = 0; i < 10000; i++) {
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