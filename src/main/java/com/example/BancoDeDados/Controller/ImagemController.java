package com.example.BancoDeDados.Controller;

import com.example.BancoDeDados.Services.ImagemQuestaoService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

@RestController
@RequestMapping("/api/imagens")
public class ImagemController {

    private final ImagemQuestaoService imagemService;

    public ImagemController(ImagemQuestaoService imagemService) {
        this.imagemService = imagemService;
    }

    /**
     * Serve imagem de questão
     * GET /api/imagens/questao/{nomeArquivo}
     */
    @GetMapping("/questao/{nomeArquivo:.+}")
    public ResponseEntity<Resource> servirImagem(@PathVariable String nomeArquivo) {
        try {
            Path caminho = imagemService.carregarImagem(nomeArquivo);
            Resource resource = new UrlResource(caminho.toUri());

            if (resource.exists() && resource.isReadable()) {
                // Detectar content type
                String contentType = detectarContentType(nomeArquivo);

                return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + nomeArquivo + "\"")
                    .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (IOException e) {
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
            return ResponseEntity.ok(Map.of("mensagem", "Imagem removida com sucesso"));
        } catch (Exception e) {
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

