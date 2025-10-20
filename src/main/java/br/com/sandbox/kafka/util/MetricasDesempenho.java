package br.com.sandbox.kafka.util;

import com.google.gson.Gson;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Classe para coletar métricas de desempenho
 */
public class MetricasDesempenho {
    private final AtomicLong totalMensagens;
    private final AtomicLong totalBytes;
    private final long tempoInicioMs;
    private volatile long tempoFimMs;
    private final AtomicLong mensagensComErro;
    private final AtomicLong mensagensSucesso;

    private static final Gson gson = new Gson();

    public MetricasDesempenho() {
        this.tempoInicioMs = System.currentTimeMillis();
        this.totalMensagens = new AtomicLong(0);
        this.totalBytes = new AtomicLong(0);
        this.mensagensComErro = new AtomicLong(0);
        this.mensagensSucesso = new AtomicLong(0);
    }

    public void registrarMensagem(long tamanhoBytes, boolean sucesso) {
        totalMensagens.incrementAndGet();
        if (tamanhoBytes > 0) {
            totalBytes.addAndGet(tamanhoBytes);
        }
        if (sucesso) {
            mensagensSucesso.incrementAndGet();
        } else {
            mensagensComErro.incrementAndGet();
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
        return duracaoSeg > 0 ? totalMensagens.get() / duracaoSeg : 0;
    }

    public double getThroughputMBPorSegundo() {
        double duracaoSeg = getDuracaoSegundos();
        double totalMB = totalBytes.get() / (1024.0 * 1024.0);
        return duracaoSeg > 0 ? totalMB / duracaoSeg : 0;
    }

    public double getTempoPorMensagemMs() {
        long total = totalMensagens.get();
        return total > 0 ? (double) getDuracaoMs() / total : 0;
    }

    public double getTaxaSucesso() {
        long total = totalMensagens.get();
        return total > 0 ? (mensagensSucesso.get() * 100.0) / total : 0;
    }

    public Map<String, Object> gerarRelatorio() {
        Map<String, Object> relatorio = new HashMap<>();
        relatorio.put("totalMensagens", totalMensagens.get());
        relatorio.put("mensagensSucesso", mensagensSucesso.get());
        relatorio.put("mensagensComErro", mensagensComErro.get());
        relatorio.put("totalBytes", totalBytes.get());
        relatorio.put("totalMB", String.format("%.2f", totalBytes.get() / (1024.0 * 1024.0)));
        relatorio.put("duracaoMs", getDuracaoMs());
        relatorio.put("duracaoSegundos", String.format("%.2f", getDuracaoSegundos()));
        relatorio.put("throughputMensagensPorSegundo", String.format("%.2f", getThroughputMensagensPorSegundo()));
        relatorio.put("throughputMBPorSegundo", String.format("%.2f", getThroughputMBPorSegundo()));
        relatorio.put("tempoPorMensagemMs", String.format("%.2f", getTempoPorMensagemMs()));
        relatorio.put("taxaSucessoPorcentagem", String.format("%.2f", getTaxaSucesso()));
        relatorio.put("timestampInicio", tempoInicioMs);
        relatorio.put("timestampFim", tempoFimMs);

        // Campos adicionais vindos do ambiente
        try {
            relatorio.put("threadsConsumidor", ConfiguracaoKafka.obterConsumerThreads());
        } catch (Throwable ignored) { }
        try {
            relatorio.put("tamanhoMensagemKB", ConfiguracaoKafka.obterTamanhoMensagemKB());
        } catch (Throwable ignored) { }
        try {
            relatorio.put("modoBenchmark", ConfiguracaoKafka.obterBenchMode());
        } catch (Throwable ignored) { }
        try {
            relatorio.put("compressaoProdutor", ConfiguracaoKafka.obterCompressionType());
        } catch (Throwable ignored) { }

        return relatorio;
    }

    public String gerarRelatorioJson() {
        return gson.toJson(gerarRelatorio());
    }

    // Extra: combinar métricas (útil se agregarmos métricas entre processos)
    public void merge(MetricasDesempenho other) {
        this.totalMensagens.addAndGet(other.getTotalMensagens());
        this.totalBytes.addAndGet(other.getTotalBytes());
        this.mensagensComErro.addAndGet(other.getMensagensComErro());
        this.mensagensSucesso.addAndGet(other.getMensagensSucesso());
    }

    public void imprimirRelatorio() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("RELATÓRIO DE DESEMPENHO");
        System.out.println("=".repeat(60));
        System.out.println("Total de mensagens: " + totalMensagens.get());
        System.out.println("Mensagens com sucesso: " + mensagensSucesso.get());
        System.out.println("Mensagens com erro: " + mensagensComErro.get());
        System.out.println("Total de dados: " + String.format("%.2f MB", totalBytes.get() / (1024.0 * 1024.0)));
        System.out.println("Duração: " + String.format("%.2f segundos", getDuracaoSegundos()));
        System.out.println("Throughput: " + String.format("%.2f mensagens/seg", getThroughputMensagensPorSegundo()));
        System.out.println("Throughput: " + String.format("%.2f MB/seg", getThroughputMBPorSegundo()));
        System.out.println("Latência média: " + String.format("%.2f ms", getTempoPorMensagemMs()));
        System.out.println("Taxa de sucesso: " + String.format("%.2f%%", getTaxaSucesso()));
        System.out.println("=".repeat(60) + "\n");
    }

    // Getters
    public long getTotalMensagens() { return totalMensagens.get(); }
    public long getTotalBytes() { return totalBytes.get(); }
    public long getMensagensComErro() { return mensagensComErro.get(); }
    public long getMensagensSucesso() { return mensagensSucesso.get(); }
}