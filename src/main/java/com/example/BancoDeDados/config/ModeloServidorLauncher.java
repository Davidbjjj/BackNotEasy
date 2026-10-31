package com.example.BancoDeDados.config;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Sobe o ml-service (Python) que executa o modelo.joblib da raiz do projeto
 * junto com a aplicação, e o encerra quando a aplicação para.
 * Se o serviço já estiver respondendo em modelo.url, não faz nada.
 */
@Component
public class ModeloServidorLauncher {

    private static final Logger log = LoggerFactory.getLogger(ModeloServidorLauncher.class);

    @Value("${modelo.url:http://localhost:8000}")
    private String modeloUrl;

    @Value("${modelo.autostart:true}")
    private boolean autostart;

    @Value("${modelo.python:python}")
    private String python;

    @Value("${modelo.dir:ml-service}")
    private String dir;

    private Process processo;

    @EventListener(ApplicationReadyEvent.class)
    public void iniciar() {
        if (!autostart) return;
        if (respondendo()) {
            log.info("Serviço do modelo já está ativo em {}", modeloUrl);
            return;
        }

        File raiz = new File(System.getProperty("user.dir"));
        File script = new File(raiz, dir + File.separator + "servico_modelo.py");
        File modelo = new File(raiz, "modelo.joblib");
        if (!script.isFile() || !modelo.isFile()) {
            log.error("Não foi possível iniciar o modelo: script={} ({}) modelo={} ({})",
                    script, script.isFile(), modelo, modelo.isFile());
            return;
        }

        List<String> cmd = new ArrayList<>(List.of(python, script.getAbsolutePath()));
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.INHERIT);
        pb.environment().put("MODELO_PATH", modelo.getAbsolutePath());
        pb.environment().put("PORT", String.valueOf(URI.create(modeloUrl).getPort()));
        pb.environment().put("PYTHONIOENCODING", "utf-8");
        try {
            processo = pb.start();
            log.info("Serviço do modelo iniciado ({}). Usando {}", String.join(" ", cmd), modelo);
        } catch (Exception e) {
            log.error("Falha ao iniciar o serviço do modelo com '{}': {}. "
                    + "Defina modelo.python com o caminho do Python que tem scikit-learn e joblib.", python, e.getMessage());
        }
    }

    private boolean respondendo() {
        try {
            HttpURLConnection c = (HttpURLConnection) URI.create(modeloUrl + "/saude").toURL().openConnection();
            c.setConnectTimeout(1000);
            c.setReadTimeout(1000);
            return c.getResponseCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    @PreDestroy
    public void parar() {
        if (processo != null && processo.isAlive()) {
            processo.destroy();
            log.info("Serviço do modelo encerrado.");
        }
    }
}
