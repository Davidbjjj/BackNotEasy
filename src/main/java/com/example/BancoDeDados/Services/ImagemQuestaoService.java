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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Serviço para gerenciar imagens de questões
 * Copia imagens temporárias para storage permanente e gera URLs públicas
 */
@Service
public class ImagemQuestaoService {

    private static final Logger logger = LoggerFactory.getLogger(ImagemQuestaoService.class);

    private final QuestaoImagemRepository imagemRepository;


    public ImagemQuestaoService(QuestaoImagemRepository imagemRepository) {
        this.imagemRepository = imagemRepository;
    }

    private String gerarEtag(byte[] dados, String nomeArquivo) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(dados);
            md.update(nomeArquivo.getBytes());
            byte[] hash = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return UUID.randomUUID().toString();
        }
    }

    /**
     * Salva imagens associadas a uma questão
     * Armazena as imagens diretamente no banco de dados
     */
    @Transactional
    public List<QuestaoImagem> salvarImagensQuestao(Questao questao,
                                                     List<String> caminhoTemporarios,
                                                     List<String> textosOcr) throws IOException {

        List<QuestaoImagem> imagens = new ArrayList<>();

        for (int i = 0; i < caminhoTemporarios.size(); i++) {
            String caminhoTemp = caminhoTemporarios.get(i);
            File arquivoTemp = new File(caminhoTemp);

            if (!arquivoTemp.exists()) {
                logger.warn("Arquivo temporário não encontrado: {}", caminhoTemp);
                continue;
            }

            // Ler dados da imagem
            byte[] dadosImagem = Files.readAllBytes(arquivoTemp.toPath());

            // Gerar nome único para referência
            String nomeOriginal = arquivoTemp.getName();
            String extensao = nomeOriginal.substring(nomeOriginal.lastIndexOf('.'));
            String nomeUnico = UUID.randomUUID().toString() + extensao;

            // Analisar texto OCR para detectar onde exibir a imagem
            String textoOcr = i < textosOcr.size() ? textosOcr.get(i) : "";
            DeteccaoExibicao deteccao = detectarOndeExibir(textoOcr, questao.getEnunciado());

            // Criar registro no banco com dados da imagem
            QuestaoImagem imagem = QuestaoImagem.builder()
                .questao(questao)
                .nomeArquivo(nomeUnico)
                .dadosImagem(dadosImagem)
                .tipoMime(detectarMimeType(nomeOriginal))
                .tamanhoBytes((long) dadosImagem.length)
                .ordem(i)
                .textoOcr(textoOcr)
                .exibirNoEnunciado(deteccao.exibirNoEnunciado)
                .exibirNasAlternativas(deteccao.exibirNasAlternativas)
                .etag(gerarEtag(dadosImagem, nomeUnico))
                .build();

            imagens.add(imagem);

            logger.debug("Imagem {} configurada: enunciado={}, alternativas={}, tamanho={}KB",
                        nomeUnico, deteccao.exibirNoEnunciado, deteccao.exibirNasAlternativas,
                        dadosImagem.length / 1024);
        }

        // Salvar no banco
        List<QuestaoImagem> salvas = imagemRepository.saveAll(imagens);
        logger.info("Salvas {} imagens no banco para questão ID {}", salvas.size(), questao.getId());

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

        // Deletar registros do banco (imagens são deletadas automaticamente)
        imagemRepository.deleteByQuestaoId(questaoId);
        logger.info("Deletadas {} imagens da questão ID {} do banco de dados", imagens.size(), questaoId);
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

        // Ler dados da imagem
        byte[] dadosImagem = file.getBytes();

        // Gerar nome único
        String nomeOriginal = file.getOriginalFilename();
        String extensao = nomeOriginal != null && nomeOriginal.contains(".")
            ? nomeOriginal.substring(nomeOriginal.lastIndexOf('.'))
            : ".png";
        String nomeUnico = UUID.randomUUID().toString() + extensao;

        // Determinar ordem se não fornecida
        if (ordem == null) {
            List<QuestaoImagem> existentes = imagemRepository.findByQuestaoIdOrderByOrdemAsc(questaoId);
            ordem = existentes.isEmpty() ? 0 : existentes.get(existentes.size() - 1).getOrdem() + 1;
        }

        // Criar registro com dados da imagem
        QuestaoImagem imagem = QuestaoImagem.builder()
            .questao(questao)
            .nomeArquivo(nomeUnico)
            .dadosImagem(dadosImagem)
            .tipoMime(file.getContentType() != null ? file.getContentType() : detectarMimeType(nomeOriginal))
            .tamanhoBytes(file.getSize())
            .ordem(ordem)
            .exibirNoEnunciado(exibirNoEnunciado != null ? exibirNoEnunciado : true)
            .exibirNasAlternativas(exibirNasAlternativas != null ? exibirNasAlternativas : false)
            .etag(gerarEtag(dadosImagem, nomeUnico))
            .build();

        QuestaoImagem salva = imagemRepository.save(imagem);
        logger.info("Imagem manual adicionada no banco à questão {}: {} ({}KB)",
                   questaoId, nomeUnico, dadosImagem.length / 1024);

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
    public void deletarImagem(Long imagemId) {
        QuestaoImagem imagem = imagemRepository.findById(imagemId)
            .orElseThrow(() -> new RuntimeException("Imagem não encontrada: " + imagemId));

        // Deletar registro do banco (dados da imagem são deletados automaticamente)
        imagemRepository.deleteById(imagemId);
        logger.info("Imagem {} removida da questão {} do banco de dados", imagemId, imagem.getQuestao().getId());
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
     * Carrega dados da imagem do banco de dados
     */
    public QuestaoImagem carregarImagem(String nomeArquivo) {
        return imagemRepository.findByNomeArquivo(nomeArquivo)
            .orElseThrow(() -> new RuntimeException("Imagem não encontrada: " + nomeArquivo));
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

