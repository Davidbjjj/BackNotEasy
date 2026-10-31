package com.example.BancoDeDados.service;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

@Service
public class OcrService {
    private static final Logger logger = LoggerFactory.getLogger(OcrService.class);

    private volatile boolean tesseractAvailable = true;
    private volatile boolean initialized = false;
    private String configuredDatapath = null;
    private String configuredLanguage = "eng";

    // ThreadLocal para garantir que cada thread tenha sua própria instância do Tesseract
    private final ThreadLocal<ITesseract> tesseractThreadLocal = ThreadLocal.withInitial(() -> {
        try {
            Tesseract instance = new Tesseract();
            if (configuredDatapath != null) {
                instance.setDatapath(configuredDatapath);
            }
            instance.setLanguage(configuredLanguage);
            logger.debug("Instância Tesseract criada para thread {}", Thread.currentThread().getName());
            return instance;
        } catch (Exception e) {
            logger.error("Erro ao criar instância Tesseract para thread: {}", e.getMessage());
            return null;
        }
    });

    public OcrService() {
        // Lazy initialization - não inicializar no construtor para evitar falhas na startup
    }

    private synchronized void initializeTesseract() {
        if (initialized) return;

        try {
            logger.info("Inicializando configuração do Tesseract OCR...");

            // Tentar configurações comuns do Windows (incluindo pasta pai)
            String[] possibleBasePaths = {
                "C:\\Program Files\\Tesseract-OCR",
                "C:\\Program Files (x86)\\Tesseract-OCR",
                System.getenv("TESSDATA_PREFIX"),
                // Adicionar caminho do projeto como fallback
                new File("src/main/resources/Arquivos").getAbsolutePath()
            };

            for (String basePath : possibleBasePaths) {
                if (basePath != null) {
                    // Verificar se é o caminho direto do tessdata
                    File tessdata = new File(basePath, "tessdata");
                    if (tessdata.exists() && tessdata.isDirectory()) {
                        configuredDatapath = basePath;
                        logger.info("Tesseract datapath configurado para: {}", basePath);
                        break;
                    }
                    // Ou se já é a pasta tessdata
                    else if (basePath.endsWith("tessdata") && new File(basePath).exists()) {
                        configuredDatapath = new File(basePath).getParent();
                        logger.info("Tesseract datapath configurado para: {}", configuredDatapath);
                        break;
                    }
                    // Se for a pasta Arquivos do projeto com por.traineddata
                    else if (new File(basePath, "por.traineddata").exists()) {
                        configuredDatapath = basePath;
                        logger.info("Tesseract datapath configurado para pasta do projeto: {}", basePath);
                        break;
                    }
                }
            }

            if (configuredDatapath == null) {
                logger.warn("Nenhum datapath configurado. Tesseract usará configuração padrão do sistema.");
            }

            // Detectar idioma disponível (prioridade: por -> eng)
            configuredLanguage = "eng"; // Padrão seguro

            if (configuredDatapath != null) {
                // Verificar diretamente na pasta configurada (para caso do projeto)
                if (new File(configuredDatapath, "por.traineddata").exists()) {
                    configuredLanguage = "por";
                    logger.info("Idioma português encontrado e será utilizado");
                } else {
                    // Verificar em tessdata/ (para instalação padrão)
                    File tessdataDir = new File(configuredDatapath, "tessdata");
                    if (tessdataDir.exists()) {
                        if (new File(tessdataDir, "por.traineddata").exists()) {
                            configuredLanguage = "por";
                            logger.info("Idioma português encontrado em tessdata/ e será utilizado");
                        } else if (new File(tessdataDir, "eng.traineddata").exists()) {
                            logger.info("Usando idioma inglês (português não encontrado)");
                        } else {
                            logger.warn("Nenhum arquivo traineddata encontrado em {}. OCR pode falhar.", tessdataDir);
                        }
                    }
                }
            }

            logger.info("Idioma do OCR configurado para: {}", configuredLanguage);

            tesseractAvailable = true;
            initialized = true;
            logger.info("Tesseract OCR configurado com sucesso (thread-safe mode)");

        } catch (Exception e) {
            logger.error("Falha ao configurar Tesseract OCR. OCR será desabilitado. Erro: {}", e.getMessage());
            logger.error("Certifique-se de que o Tesseract está instalado: https://github.com/UB-Mannheim/tesseract/wiki");
            tesseractAvailable = false;
            initialized = true;
        }
    }

    public String doOcr(File image) {
        if (!initialized) {
            initializeTesseract();
        }

        if (!tesseractAvailable) {
            logger.warn("Tesseract não está disponível. Retornando texto vazio para: {}", image.getName());
            return "";
        }

        // Obter instância thread-local do Tesseract
        ITesseract tesseract = tesseractThreadLocal.get();
        if (tesseract == null) {
            logger.error("Não foi possível criar instância Tesseract para thread atual");
            return "";
        }

        try {
            BufferedImage bi = ImageIO.read(image);
            if (bi == null) {
                logger.warn("Não foi possível ler a imagem: {}", image.getName());
                return "";
            }

            // Tentar OCR com timeout implícito e tratamento de erro
            String result = tesseract.doOCR(bi);

            if (result == null || result.isBlank()) {
                logger.debug("OCR não encontrou texto em: {}", image.getName());
                return "";
            }

            logger.debug("OCR extraiu {} caracteres de: {} (thread: {})",
                        result.length(), image.getName(), Thread.currentThread().getName());
            return result.trim();

        } catch (TesseractException e) {
            logger.error("Erro do Tesseract ao processar {}: {}", image.getName(), e.getMessage());
            return "";

        } catch (Error e) {
            // Captura java.lang.Error (como Invalid memory access)
            logger.error("Erro crítico de JNA/memória ao processar {}: {}. Thread: {}",
                        image.getName(), e.getMessage(), Thread.currentThread().getName());
            // Não desabilitar OCR globalmente - pode ser problema específico desta thread
            return "";

        } catch (Exception e) {
            logger.error("Erro inesperado ao processar OCR de {}: {}", image.getName(), e.getMessage());
            return "";
        }
    }

    public boolean isTesseractAvailable() {
        if (!initialized) {
            initializeTesseract();
        }
        return tesseractAvailable;
    }

    /**
     * Limpa recursos da thread atual (chamado automaticamente pelo pool de threads)
     */
    public void cleanupCurrentThread() {
        tesseractThreadLocal.remove();
        logger.debug("Recursos Tesseract liberados para thread {}", Thread.currentThread().getName());
    }
}

