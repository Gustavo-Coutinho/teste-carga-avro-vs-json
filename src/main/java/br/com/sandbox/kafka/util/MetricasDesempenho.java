package br.com.sandbox.kafka.util;

import com.google.gson.Gson;
import java.util.HashMap;
import java.util.Map;

/**
 * Classe para coletar métricas de desempenho
 */
public class MetricasDesempenho {
    private long totalMensagens;
    private long totalBytes;
    private long tempoInicioMs;
    private long tempoFimMs;
    private long mensagensComErro;
    private long mensagensSucesso;

    private static final Gson gson = new Gson();

    public MetricasDesempenho() {
        this.tempoInicioMs = System.currentTimeMillis();
        this.totalMensagens = 0;
        this.totalBytes = 0;
        this.mensagensComErro = 0;
        this.mensagensSucesso = 0;
    }

    public void registrarMensagem(long tamanhoBytes, boolean sucesso) {
        totalMensagens++;
        totalBytes += tamanhoBytes;

        if (sucesso) {
            mensagensSucesso++;
        } else {
            mensagensComErro++;
        }
    }

    public void finalizar() {
        this.tempoFimMs = System.currentTimeMillis();
    }

    public long getDuracaoMs() {
        return tempoFimMs - tempoInicioMs;
    }

    public double getDuracaoSegundos() {
        return getDuracaoMs() / 1000.0;
    }

    public double getThroughputMensagensPorSegundo() {
        double duracaoSeg = getDuracaoSegundos();
        return duracaoSeg > 0 ? totalMensagens / duracaoSeg : 0;
    }

    public double getThroughputMBPorSegundo() {
        double duracaoSeg = getDuracaoSegundos();
        double totalMB = totalBytes / (1024.0 * 1024.0);
        return duracaoSeg > 0 ? totalMB / duracaoSeg : 0;
    }

    public double getLatenciaMediaMs() {
        return totalMensagens > 0 ? (double) getDuracaoMs() / totalMensagens : 0;
    }

    public double getTaxaSucesso() {
        return totalMensagens > 0 ? (mensagensSucesso * 100.0) / totalMensagens : 0;
    }

    public Map<String, Object> gerarRelatorio() {
        Map<String, Object> relatorio = new HashMap<>();
        relatorio.put("totalMensagens", totalMensagens);
        relatorio.put("mensagensSucesso", mensagensSucesso);
        relatorio.put("mensagensComErro", mensagensComErro);
        relatorio.put("totalBytes", totalBytes);
        relatorio.put("totalMB", String.format("%.2f", totalBytes / (1024.0 * 1024.0)));
        relatorio.put("duracaoMs", getDuracaoMs());
        relatorio.put("duracaoSegundos", String.format("%.2f", getDuracaoSegundos()));
        relatorio.put("throughputMensagensPorSegundo", String.format("%.2f", getThroughputMensagensPorSegundo()));
        relatorio.put("throughputMBPorSegundo", String.format("%.2f", getThroughputMBPorSegundo()));
        relatorio.put("latenciaMediaMs", String.format("%.2f", getLatenciaMediaMs()));
        relatorio.put("taxaSucessoPorcentagem", String.format("%.2f", getTaxaSucesso()));
        relatorio.put("timestampInicio", tempoInicioMs);
        relatorio.put("timestampFim", tempoFimMs);

        return relatorio;
    }

    public String gerarRelatorioJson() {
        return gson.toJson(gerarRelatorio());
    }

    public void imprimirRelatorio() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("RELATÓRIO DE DESEMPENHO");
        System.out.println("=".repeat(60));
        System.out.println("Total de mensagens: " + totalMensagens);
        System.out.println("Mensagens com sucesso: " + mensagensSucesso);
        System.out.println("Mensagens com erro: " + mensagensComErro);
        System.out.println("Total de dados: " + String.format("%.2f MB", totalBytes / (1024.0 * 1024.0)));
        System.out.println("Duração: " + String.format("%.2f segundos", getDuracaoSegundos()));
        System.out.println("Throughput: " + String.format("%.2f mensagens/seg", getThroughputMensagensPorSegundo()));
        System.out.println("Throughput: " + String.format("%.2f MB/seg", getThroughputMBPorSegundo()));
        System.out.println("Latência média: " + String.format("%.2f ms", getLatenciaMediaMs()));
        System.out.println("Taxa de sucesso: " + String.format("%.2f%%", getTaxaSucesso()));
        System.out.println("=".repeat(60) + "\n");
    }

    // Getters
    public long getTotalMensagens() { return totalMensagens; }
    public long getTotalBytes() { return totalBytes; }
    public long getMensagensComErro() { return mensagensComErro; }
    public long getMensagensSucesso() { return mensagensSucesso; }
}