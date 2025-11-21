package com.example.BancoDeDados.Services;

import com.example.BancoDeDados.Model.Questao;
import com.example.BancoDeDados.Model.QuestaoImagem;
import com.example.BancoDeDados.Repositores.QuestaoImagemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Serviço para gerenciar imagens de questões
 * Copia imagens temporárias para storage permanente e gera URLs públicas
 */
@Service
public class ImagemQuestaoService {

    private static final Logger logger = LoggerFactory.getLogger(ImagemQuestaoService.class);

    private final QuestaoImagemRepository imagemRepository;

    @Value("${app.upload.dir:uploads/questoes}")
    private String uploadDir;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    public ImagemQuestaoService(QuestaoImagemRepository imagemRepository) {
        this.imagemRepository = imagemRepository;
    }

    /**
     * Salva imagens associadas a uma questão
     * Copia arquivos temporários para storage permanente
     */
    @Transactional
    public List<QuestaoImagem> salvarImagensQuestao(Questao questao,
                                                     List<String> caminhoTemporarios,
                                                     List<String> textosOcr) throws IOException {

        List<QuestaoImagem> imagens = new ArrayList<>();

        // Criar diretório de upload se não existir
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
            logger.info("Diretório de upload criado: {}", uploadPath.toAbsolutePath());
        }

        for (int i = 0; i < caminhoTemporarios.size(); i++) {
            String caminhoTemp = caminhoTemporarios.get(i);
            File arquivoTemp = new File(caminhoTemp);

            if (!arquivoTemp.exists()) {
                logger.warn("Arquivo temporário não encontrado: {}", caminhoTemp);
                continue;
            }

            // Gerar nome único para o arquivo
            String nomeOriginal = arquivoTemp.getName();
            String extensao = nomeOriginal.substring(nomeOriginal.lastIndexOf('.'));
            String nomeUnico = UUID.randomUUID().toString() + extensao;

            // Copiar para storage permanente
            Path destino = uploadPath.resolve(nomeUnico);
            Files.copy(arquivoTemp.toPath(), destino, StandardCopyOption.REPLACE_EXISTING);

            // Gerar URL pública
            String urlPublica = baseUrl + "/api/imagens/questao/" + nomeUnico;

            // Analisar texto OCR para detectar onde exibir a imagem
            String textoOcr = i < textosOcr.size() ? textosOcr.get(i) : "";
            DeteccaoExibicao deteccao = detectarOndeExibir(textoOcr, questao.getEnunciado());

            // Criar registro no banco
            QuestaoImagem imagem = QuestaoImagem.builder()
                .questao(questao)
                .nomeArquivo(nomeUnico)
                .caminhoArquivo(destino.toAbsolutePath().toString())
                .urlPublica(urlPublica)
                .tipoMime(detectarMimeType(nomeOriginal))
                .tamanhoBytes(arquivoTemp.length())
                .ordem(i)
                .textoOcr(textoOcr)
                .exibirNoEnunciado(deteccao.exibirNoEnunciado)
                .exibirNasAlternativas(deteccao.exibirNasAlternativas)
                .build();

            imagens.add(imagem);

            logger.debug("Imagem {} configurada: enunciado={}, alternativas={}",
                        nomeUnico, deteccao.exibirNoEnunciado, deteccao.exibirNasAlternativas);

            logger.debug("Imagem copiada: {} -> {}", caminhoTemp, destino);
        }

        // Salvar no banco
        List<QuestaoImagem> salvas = imagemRepository.saveAll(imagens);
        logger.info("Salvas {} imagens para questão ID {}", salvas.size(), questao.getId());

        return salvas;
    }

    /**
     * Busca imagens de uma questão
     */
    public List<QuestaoImagem> buscarImagensQuestao(Integer questaoId) {
        return imagemRepository.findByQuestaoIdOrderByOrdemAsc(questaoId);
    }

    /**
     * Deleta imagens de uma questão
     */
    @Transactional
    public void deletarImagensQuestao(Integer questaoId) {
        List<QuestaoImagem> imagens = imagemRepository.findByQuestaoIdOrderByOrdemAsc(questaoId);

        // Deletar arquivos físicos
        for (QuestaoImagem imagem : imagens) {
            try {
                Path caminho = Paths.get(imagem.getCaminhoArquivo());
                Files.deleteIfExists(caminho);
                logger.debug("Arquivo deletado: {}", caminho);
            } catch (IOException e) {
                logger.error("Erro ao deletar arquivo: {}", imagem.getCaminhoArquivo(), e);
            }
        }

        // Deletar registros do banco
        imagemRepository.deleteByQuestaoId(questaoId);
        logger.info("Deletadas {} imagens da questão ID {}", imagens.size(), questaoId);
    }

    /**
     * Adiciona uma imagem manualmente a uma questão (via upload do usuário)
     */
    @Transactional
    public QuestaoImagem adicionarImagemManual(Integer questaoId,
                                               org.springframework.web.multipart.MultipartFile file,
                                               Boolean exibirNoEnunciado,
                                               Boolean exibirNasAlternativas,
                                               Integer ordem) throws IOException {

        // Buscar questão
        Questao questao = new Questao();
        questao.setId(questaoId); // Simplificado, em produção buscar do repository

        // Criar diretório se não existir
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // Gerar nome único
        String nomeOriginal = file.getOriginalFilename();
        String extensao = nomeOriginal != null && nomeOriginal.contains(".")
            ? nomeOriginal.substring(nomeOriginal.lastIndexOf('.'))
            : ".png";
        String nomeUnico = UUID.randomUUID().toString() + extensao;

        // Salvar arquivo
        Path destino = uploadPath.resolve(nomeUnico);
        Files.copy(file.getInputStream(), destino, StandardCopyOption.REPLACE_EXISTING);

        // Determinar ordem se não fornecida
        if (ordem == null) {
            List<QuestaoImagem> existentes = imagemRepository.findByQuestaoIdOrderByOrdemAsc(questaoId);
            ordem = existentes.isEmpty() ? 0 : existentes.get(existentes.size() - 1).getOrdem() + 1;
        }

        // Criar registro
        QuestaoImagem imagem = QuestaoImagem.builder()
            .questao(questao)
            .nomeArquivo(nomeUnico)
            .caminhoArquivo(destino.toAbsolutePath().toString())
            .urlPublica(baseUrl + "/api/imagens/questao/" + nomeUnico)
            .tipoMime(file.getContentType() != null ? file.getContentType() : detectarMimeType(nomeOriginal))
            .tamanhoBytes(file.getSize())
            .ordem(ordem)
            .exibirNoEnunciado(exibirNoEnunciado != null ? exibirNoEnunciado : true)
            .exibirNasAlternativas(exibirNasAlternativas != null ? exibirNasAlternativas : false)
            .build();

        QuestaoImagem salva = imagemRepository.save(imagem);
        logger.info("Imagem manual adicionada à questão {}: {}", questaoId, nomeUnico);

        return salva;
    }

    /**
     * Atualiza configurações de exibição de uma imagem
     */
    @Transactional
    public QuestaoImagem atualizarConfiguracoesImagem(Long imagemId,
                                                      Boolean exibirNoEnunciado,
                                                      Boolean exibirNasAlternativas,
                                                      Integer ordem) {

        QuestaoImagem imagem = imagemRepository.findById(imagemId)
            .orElseThrow(() -> new RuntimeException("Imagem não encontrada: " + imagemId));

        if (exibirNoEnunciado != null) {
            imagem.setExibirNoEnunciado(exibirNoEnunciado);
        }
        if (exibirNasAlternativas != null) {
            imagem.setExibirNasAlternativas(exibirNasAlternativas);
        }
        if (ordem != null) {
            imagem.setOrdem(ordem);
        }

        QuestaoImagem atualizada = imagemRepository.save(imagem);
        logger.info("Imagem {} atualizada: enunciado={}, alternativas={}, ordem={}",
                   imagemId, exibirNoEnunciado, exibirNasAlternativas, ordem);

        return atualizada;
    }

    /**
     * Deleta uma imagem específica
     */
    @Transactional
    public void deletarImagem(Long imagemId) throws IOException {
        QuestaoImagem imagem = imagemRepository.findById(imagemId)
            .orElseThrow(() -> new RuntimeException("Imagem não encontrada: " + imagemId));

        // Deletar arquivo físico
        try {
            Path caminho = Paths.get(imagem.getCaminhoArquivo());
            Files.deleteIfExists(caminho);
            logger.info("Arquivo deletado: {}", caminho);
        } catch (IOException e) {
            logger.error("Erro ao deletar arquivo físico: {}", imagem.getCaminhoArquivo(), e);
            throw e;
        }

        // Deletar registro do banco
        imagemRepository.deleteById(imagemId);
        logger.info("Imagem {} removida da questão {}", imagemId, imagem.getQuestao().getId());
    }

    /**
     * Detecta MIME type pelo nome do arquivo
     */
    private String detectarMimeType(String nomeArquivo) {
        String extensao = nomeArquivo.toLowerCase();
        if (extensao.endsWith(".png")) return "image/png";
        if (extensao.endsWith(".jpg") || extensao.endsWith(".jpeg")) return "image/jpeg";
        if (extensao.endsWith(".gif")) return "image/gif";
        if (extensao.endsWith(".webp")) return "image/webp";
        return "application/octet-stream";
    }

    /**
     * Carrega arquivo físico da imagem
     */
    public Path carregarImagem(String nomeArquivo) throws IOException {
        Path caminho = Paths.get(uploadDir).resolve(nomeArquivo);

        if (!Files.exists(caminho)) {
            throw new IOException("Arquivo não encontrado: " + nomeArquivo);
        }

        return caminho;
    }

    /**
     * Detecta automaticamente onde a imagem deve ser exibida baseado no texto OCR
     * Analisa se contém apenas enunciado, apenas alternativas, ou ambos
     */
    private DeteccaoExibicao detectarOndeExibir(String textoOcr, String enunciadoQuestao) {
        DeteccaoExibicao deteccao = new DeteccaoExibicao();

        if (textoOcr == null || textoOcr.isBlank()) {
            // Sem OCR, exibir apenas no enunciado (padrão seguro)
            deteccao.exibirNoEnunciado = true;
            deteccao.exibirNasAlternativas = false;
            return deteccao;
        }

        String textoNormalizado = textoOcr.toLowerCase().trim();

        // Detectar se contém alternativas (padrões: a), b), c), etc.)
        boolean contemAlternativas = contemPadraoAlternativas(textoNormalizado);

        // Detectar se contém texto do enunciado
        boolean contemEnunciado = contemTextoEnunciado(textoNormalizado, enunciadoQuestao);

        // Detectar se é imagem completa (tem cabeçalho de questão)
        boolean imagemCompleta = contemPadraoQuestao(textoNormalizado);

        // Lógica de decisão
        if (imagemCompleta || (contemEnunciado && contemAlternativas)) {
            // Imagem completa: mostra tudo junto (geralmente no enunciado)
            deteccao.exibirNoEnunciado = true;
            deteccao.exibirNasAlternativas = false;
            logger.debug("Detectada imagem completa (enunciado + alternativas juntos)");

        } else if (contemAlternativas && !contemEnunciado) {
            // Apenas alternativas: mostrar nas alternativas
            deteccao.exibirNoEnunciado = false;
            deteccao.exibirNasAlternativas = true;
            logger.debug("Detectada imagem apenas com alternativas");

        } else {
            // Padrão: apenas enunciado ou incerto
            deteccao.exibirNoEnunciado = true;
            deteccao.exibirNasAlternativas = false;
            logger.debug("Detectada imagem apenas com enunciado");
        }

        return deteccao;
    }

    /**
     * Verifica se o texto contém padrão de alternativas (a), b), c), etc.)
     */
    private boolean contemPadraoAlternativas(String texto) {
        // Padrões de alternativas
        String[] padroes = {
            "a\\s*\\)",  // a)
            "b\\s*\\)",  // b)
            "c\\s*\\)",  // c)
            "\\ba\\)",   // word boundary + a)
            "\\bb\\)",   // word boundary + b)
            "alternativa"
        };

        for (String padrao : padroes) {
            if (texto.matches("(?s).*" + padrao + ".*")) {
                // Verificar se tem pelo menos 2 alternativas
                long count = texto.chars().filter(ch -> ch == ')').count();
                if (count >= 2) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Verifica se o texto OCR contém parte do enunciado da questão
     */
    private boolean contemTextoEnunciado(String textoOcr, String enunciado) {
        if (enunciado == null || enunciado.isBlank()) {
            return false;
        }

        String enunciadoNorm = enunciado.toLowerCase().trim();

        // Extrair palavras significativas do enunciado (> 5 caracteres)
        String[] palavras = enunciadoNorm.split("\\s+");
        int palavrasEncontradas = 0;
        int palavrasSignificativas = 0;

        for (String palavra : palavras) {
            if (palavra.length() > 5) {
                palavrasSignificativas++;
                if (textoOcr.contains(palavra)) {
                    palavrasEncontradas++;
                }
            }
        }

        // Se encontrou pelo menos 30% das palavras significativas, considera que tem enunciado
        if (palavrasSignificativas > 0) {
            double percentual = (double) palavrasEncontradas / palavrasSignificativas;
            return percentual >= 0.3;
        }

        return false;
    }

    /**
     * Verifica se o texto contém padrão de cabeçalho de questão
     */
    private boolean contemPadraoQuestao(String texto) {
        String[] padroes = {
            "questão",
            "questao",
            "exercício",
            "exercicio",
            "pergunta",
            "item",
            "prova"
        };

        for (String padrao : padroes) {
            if (texto.contains(padrao)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Classe auxiliar para resultado da detecção
     */
    private static class DeteccaoExibicao {
        boolean exibirNoEnunciado = true;
        boolean exibirNasAlternativas = false;
    }
}

