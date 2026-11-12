package com.example.BancoDeDados.Controller;

import com.example.BancoDeDados.Security.CallerContext;
import com.example.BancoDeDados.Services.AlunosRiscoService;
import com.example.BancoDeDados.Services.AvisoService;
import com.example.BancoDeDados.Services.ExportacaoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * FE-204 (alunos em risco), FE-207 (perfil do aluno), FE-208 (exportar notas), FE-209 (mural de avisos) e o
 * envio de aviso a alunos (FE-203/204). Contrato em docs/professor-api.md do front.
 */
@RestController
public class ProfessorPaineisController {

    public record AvisarRequest(List<UUID> estudanteIds, String titulo, String mensagem) {}

    public record FixarRequest(Boolean fixado) {}

    @Autowired
    private AlunosRiscoService alunosRiscoService;
    @Autowired
    private ExportacaoService exportacaoService;
    @Autowired
    private AvisoService avisoService;
    @Autowired
    private com.example.BancoDeDados.Services.PermissaoService permissoes;

    private void permitir(String permissao) {
        CallerContext.exigirStaff();
        permissoes.exigir(permissao);
    }

    // ------------------------------------------------------------ alunos em risco / perfil / avisar

    @GetMapping("/professor/alunos-em-risco")
    public AlunosRiscoService.Painel alunosEmRisco(@RequestParam(required = false) UUID disciplinaId,
                                                   @RequestParam(defaultValue = "false") boolean todos,
                                                   @RequestParam(required = false) Double mediaMinima,
                                                   @RequestParam(required = false) Integer diasSemAcesso,
                                                   @RequestParam(required = false) Integer listasAtrasadas) {
        permitir(com.example.BancoDeDados.Model.GestaoInstitucional.VER_ALUNOS_RISCO);
        return alunosRiscoService.painel(CallerContext.professorIdOuNull(), CallerContext.instituicaoIdOuNull(),
                disciplinaId, todos, mediaMinima, diasSemAcesso, listasAtrasadas);
    }

    @GetMapping("/professor/alunos/{estudanteId}/perfil")
    public AlunosRiscoService.Perfil perfil(@PathVariable UUID estudanteId) {
        permitir(com.example.BancoDeDados.Model.GestaoInstitucional.VER_ALUNOS_RISCO);
        return alunosRiscoService.perfil(estudanteId, CallerContext.professorIdOuNull(), CallerContext.instituicaoIdOuNull());
    }

    @PostMapping("/professor/avisar-alunos")
    public Map<String, Integer> avisar(@RequestBody AvisarRequest body) {
        CallerContext.exigirStaff();
        int n = alunosRiscoService.avisar(CallerContext.professorIdOuNull(), CallerContext.instituicaoIdOuNull(),
                body.estudanteIds(), body.titulo(), body.mensagem());
        return Map.of("enviados", n);
    }

    // ------------------------------------------------------------ exportação de notas

    @GetMapping("/exportacoes/colunas")
    public List<Map<String, String>> colunas() {
        CallerContext.exigirStaff();
        return ExportacaoService.COLUNAS.entrySet().stream()
                .map(e -> Map.of("chave", e.getKey(), "titulo", e.getValue())).toList();
    }

    @PostMapping("/exportacoes")
    public ResponseEntity<ExportacaoService.Status> exportar(@RequestBody ExportacaoService.ExportarRequest body) {
        permitir(com.example.BancoDeDados.Model.GestaoInstitucional.EXPORTAR_NOTAS);
        var status = exportacaoService.criar(CallerContext.professorIdOuNull(), CallerContext.userId(), body);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(status);
    }

    @GetMapping("/exportacoes")
    public List<ExportacaoService.Status> exportacoes() {
        CallerContext.exigirStaff();
        return exportacaoService.listar(CallerContext.userId());
    }

    @GetMapping("/exportacoes/{id}")
    public ExportacaoService.Status exportacao(@PathVariable UUID id) {
        CallerContext.exigirStaff();
        return exportacaoService.status(id, CallerContext.userId());
    }

    @GetMapping("/exportacoes/{id}/arquivo")
    public ResponseEntity<byte[]> baixar(@PathVariable UUID id) {
        CallerContext.exigirStaff();
        var arquivo = exportacaoService.arquivo(id, CallerContext.userId());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(arquivo.nome(), StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType(arquivo.contentType()))
                .body(arquivo.dados());
    }

    // ------------------------------------------------------------ mural de avisos

    @GetMapping("/disciplinas/{disciplinaId}/avisos")
    public List<AvisoService.AvisoDTO> avisos(@PathVariable UUID disciplinaId) {
        return avisoService.listar(disciplinaId);
    }

    @PostMapping(value = "/disciplinas/{disciplinaId}/avisos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AvisoService.AvisoDTO> publicar(@PathVariable UUID disciplinaId,
                                                          @RequestParam String titulo,
                                                          @RequestParam String texto,
                                                          @RequestParam(defaultValue = "false") boolean fixado,
                                                          @RequestParam(required = false) MultipartFile anexo) {
        return ResponseEntity.status(HttpStatus.CREATED).body(avisoService.criar(disciplinaId, titulo, texto, fixado, anexo));
    }

    @PatchMapping("/avisos/{id}/fixar")
    public AvisoService.AvisoDTO fixar(@PathVariable UUID id, @RequestBody FixarRequest body) {
        return avisoService.fixar(id, Boolean.TRUE.equals(body.fixado()));
    }

    @DeleteMapping("/avisos/{id}")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {
        avisoService.excluir(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/avisos/{id}/anexo")
    public ResponseEntity<byte[]> anexo(@PathVariable UUID id) {
        var a = avisoService.anexo(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(a.nome(), StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType(a.tipo()))
                .body(a.dados());
    }
}
