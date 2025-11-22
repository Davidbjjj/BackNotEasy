package com.example.BancoDeDados.Controller;

import com.example.BancoDeDados.Services.ImagemQuestaoService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@RestController
@RequestMapping("/api/imagens")
public class ImagemController {

    private final ImagemQuestaoService imagemService;

    public ImagemController(ImagemQuestaoService imagemService) {
        this.imagemService = imagemService;
    }

    private static final DateTimeFormatter HTTP_DATE = DateTimeFormatter.RFC_1123_DATE_TIME;

    /**
     * Serve imagem de questão do banco de dados
     * GET /api/imagens/questao/{nomeArquivo}
     */
    @GetMapping("/questao/{nomeArquivo:.+}")
    public ResponseEntity<byte[]> servirImagem(@PathVariable String nomeArquivo,
                                               @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch,
                                               @RequestHeader(value = "If-Modified-Since", required = false) String ifModifiedSince) {
        try {
            var imagem = imagemService.carregarImagem(nomeArquivo);

            // Verificar se a imagem tem dados binários
            if (imagem.getDadosImagem() == null || imagem.getDadosImagem().length == 0) {
                return ResponseEntity.status(410) // 410 Gone - recurso não está mais disponível
                    .body(null);
            }

            // Verificar ETag condicional
            if (ifNoneMatch != null && ifNoneMatch.equals(imagem.getEtag())) {
                return ResponseEntity.status(304)
                        .eTag(imagem.getEtag())
                        .build();
            }

            // Verificar Last-Modified condicional
            if (ifModifiedSince != null && imagem.getUpdatedAt() != null) {
                // Parser simples - clientes normalmente mandam RFC 1123
                // Ignorar parsing robusto para simplicidade
                // Se updatedAt anterior ou igual, devolver 304
                // Comparação por segundos
                // (front pode mandar data antiga e receber 200)
            }

            String lastModified = imagem.getUpdatedAt() != null ? HTTP_DATE.format(imagem.getUpdatedAt().atOffset(ZoneOffset.UTC)) : null;

            return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(imagem.getTipoMime()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + nomeArquivo + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600")
                .eTag(imagem.getEtag())
                .header(HttpHeaders.LAST_MODIFIED, lastModified != null ? lastModified : "")
                .body(imagem.getDadosImagem());
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Lista todas as imagens de uma questão
     * GET /api/imagens/questao/{questaoId}/todas
     */
    @GetMapping("/questao/{questaoId}/todas")
    public ResponseEntity<?> listarImagensQuestao(@PathVariable Integer questaoId) {
        try {
            var imagens = imagemService.buscarImagensQuestao(questaoId);
            return ResponseEntity.ok(imagens);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("erro", e.getMessage()));
        }
    }

    /**
     * Adiciona uma nova imagem a uma questão
     * POST /api/imagens/questao/{questaoId}/adicionar
     */
    @PostMapping("/questao/{questaoId}/adicionar")
    public ResponseEntity<?> adicionarImagemQuestao(
            @PathVariable Integer questaoId,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam(value = "exibirNoEnunciado", defaultValue = "true") Boolean exibirNoEnunciado,
            @RequestParam(value = "exibirNasAlternativas", defaultValue = "false") Boolean exibirNasAlternativas,
            @RequestParam(value = "ordem", required = false) Integer ordem) {

        try {
            var imagem = imagemService.adicionarImagemManual(
                questaoId, file, exibirNoEnunciado, exibirNasAlternativas, ordem
            );
            return ResponseEntity.ok(Map.of(
                "mensagem", "Imagem adicionada com sucesso",
                "imagem", imagem
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("erro", e.getMessage()));
        }
    }

    /**
     * Atualiza configurações de exibição de uma imagem
     * PUT /api/imagens/{imagemId}
     */
    @PutMapping("/{imagemId}")
    public ResponseEntity<?> atualizarImagem(
            @PathVariable Long imagemId,
            @RequestParam(value = "exibirNoEnunciado", required = false) Boolean exibirNoEnunciado,
            @RequestParam(value = "exibirNasAlternativas", required = false) Boolean exibirNasAlternativas,
            @RequestParam(value = "ordem", required = false) Integer ordem) {

        try {
            var imagem = imagemService.atualizarConfiguracoesImagem(
                imagemId, exibirNoEnunciado, exibirNasAlternativas, ordem
            );
            return ResponseEntity.ok(Map.of(
                "mensagem", "Imagem atualizada com sucesso",
                "imagem", imagem
            ));
        } catch (Exception e) {
            return ResponseEntity.status(404).body(Map.of("erro", e.getMessage()));
        }
    }

    /**
     * Remove uma imagem de uma questão
     * DELETE /api/imagens/{imagemId}
     */
    @DeleteMapping("/{imagemId}")
    public ResponseEntity<?> removerImagem(@PathVariable Long imagemId) {
        try {
            imagemService.deletarImagem(imagemId);
            return ResponseEntity.ok(Map.of("mensagem", "Imagem removida com sucesso do banco de dados"));
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(Map.of("erro", e.getMessage()));
        }
    }

    private String detectarContentType(String nomeArquivo) {
        String extensao = nomeArquivo.toLowerCase();
        if (extensao.endsWith(".png")) return "image/png";
        if (extensao.endsWith(".jpg") || extensao.endsWith(".jpeg")) return "image/jpeg";
        if (extensao.endsWith(".gif")) return "image/gif";
        if (extensao.endsWith(".webp")) return "image/webp";
        return "application/octet-stream";
    }
}
