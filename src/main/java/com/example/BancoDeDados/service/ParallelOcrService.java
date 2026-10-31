package com.example.BancoDeDados.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Serviço otimizado para processamento paralelo de OCR em múltiplas imagens
 */
@Service
public class ParallelOcrService {

    private static final Logger logger = LoggerFactory.getLogger(ParallelOcrService.class);
    private static final int MAX_THREADS = Runtime.getRuntime().availableProcessors();

    private final OcrService ocrService;
    private final ExecutorService executorService;

    public ParallelOcrService(OcrService ocrService) {
        this.ocrService = ocrService;
        this.executorService = Executors.newFixedThreadPool(
            MAX_THREADS,
            r -> {
                Thread t = new Thread(r);
                t.setName("OCR-Worker-" + t.getId());
                t.setDaemon(true);
                return t;
            }
        );
        logger.info("ParallelOcrService inicializado com {} threads", MAX_THREADS);
    }

    /**
     * Processa OCR em múltiplos arquivos em paralelo
     *
     * @param files Lista de arquivos para processar
     * @return Mapa de filename -> texto extraído
     */
    public ConcurrentHashMap<String, String> processarEmParalelo(List<File> files) {
        if (files == null || files.isEmpty()) {
            return new ConcurrentHashMap<>();
        }

        logger.info("Iniciando processamento paralelo de {} arquivos", files.size());
        long startTime = System.currentTimeMillis();

        ConcurrentHashMap<String, String> resultados = new ConcurrentHashMap<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (File file : files) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    String texto = ocrService.doOcr(file);
                    resultados.put(file.getName(), texto);
                    logger.debug("OCR concluído para: {}", file.getName());
                } catch (Exception e) {
                    logger.error("Erro ao processar {}: {}", file.getName(), e.getMessage());
                    resultados.put(file.getName(), "");
                }
            }, executorService);

            futures.add(future);
        }

        // Aguardar todos os processos terminarem
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(5, TimeUnit.MINUTES); // Timeout de 5 minutos
        } catch (TimeoutException e) {
            logger.error("Timeout ao processar OCR em paralelo após 5 minutos");
        } catch (Exception e) {
            logger.error("Erro durante processamento paralelo: {}", e.getMessage());
        }

        long duration = System.currentTimeMillis() - startTime;
        logger.info("Processamento paralelo concluído em {}ms ({} arquivos)",
                   duration, files.size());

        return resultados;
    }

    /**
     * Processa OCR com prioridade para páginas específicas
     * Útil quando queremos processar páginas iniciais primeiro
     */
    public ConcurrentHashMap<String, String> processarComPrioridade(
            List<File> files,
            int primeirasPaginas) {

        if (files == null || files.isEmpty()) {
            return new ConcurrentHashMap<>();
        }

        ConcurrentHashMap<String, String> resultados = new ConcurrentHashMap<>();

        // Dividir em alta prioridade e normal
        List<File> altaPrioridade = files.subList(0, Math.min(primeirasPaginas, files.size()));
        List<File> normal = files.size() > primeirasPaginas
            ? files.subList(primeirasPaginas, files.size())
            : new ArrayList<>();

        logger.info("Processando {} arquivos de alta prioridade primeiro", altaPrioridade.size());

        // Processar alta prioridade primeiro
        ConcurrentHashMap<String, String> resultadosAlta = processarEmParalelo(altaPrioridade);
        resultados.putAll(resultadosAlta);

        // Processar restante
        if (!normal.isEmpty()) {
            logger.info("Processando {} arquivos restantes", normal.size());
            ConcurrentHashMap<String, String> resultadosNormal = processarEmParalelo(normal);
            resultados.putAll(resultadosNormal);
        }

        return resultados;
    }

    /**
     * Processa com callback para progresso
     */
    public ConcurrentHashMap<String, String> processarComProgresso(
            List<File> files,
            ProgressCallback callback) {

        if (files == null || files.isEmpty()) {
            return new ConcurrentHashMap<>();
        }

        ConcurrentHashMap<String, String> resultados = new ConcurrentHashMap<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        int total = files.size();
        AtomicInteger processados = new AtomicInteger(0);

        for (File file : files) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    String texto = ocrService.doOcr(file);
                    resultados.put(file.getName(), texto);

                    int atual = processados.incrementAndGet();
                    double progresso = (atual * 100.0) / total;

                    if (callback != null) {
                        callback.onProgress(atual, total, progresso, file.getName());
                    }

                } catch (Exception e) {
                    logger.error("Erro ao processar {}: {}", file.getName(), e.getMessage());
                    resultados.put(file.getName(), "");
                    processados.incrementAndGet();
                }
            }, executorService);

            futures.add(future);
        }

        // Aguardar conclusão
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(5, TimeUnit.MINUTES);
        } catch (Exception e) {
            logger.error("Erro durante processamento: {}", e.getMessage());
        }

        return resultados;
    }

    /**
     * Verifica saúde do serviço
     */
    public boolean isHealthy() {
        return ocrService.isTesseractAvailable() && !executorService.isShutdown();
    }

    /**
     * Obter estatísticas
     */
    public String getStats() {
        ThreadPoolExecutor tpe = (ThreadPoolExecutor) executorService;
        return String.format(
            "Threads: %d/%d, Tarefas: %d completadas, %d ativas",
            tpe.getActiveCount(),
            tpe.getMaximumPoolSize(),
            tpe.getCompletedTaskCount(),
            tpe.getActiveCount()
        );
    }

    /**
     * Interface para callback de progresso
     */
    @FunctionalInterface
    public interface ProgressCallback {
        void onProgress(int processados, int total, double percentual, String arquivoAtual);
    }
}

