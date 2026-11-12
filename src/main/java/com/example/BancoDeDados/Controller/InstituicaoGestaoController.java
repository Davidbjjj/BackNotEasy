package com.example.BancoDeDados.Controller;

import com.example.BancoDeDados.Model.Estudante;
import com.example.BancoDeDados.Model.Instituicao;
import com.example.BancoDeDados.Model.Professor;
import com.example.BancoDeDados.Repositores.EstudanteRepositores;
import com.example.BancoDeDados.Repositores.InstituicaoRepository;
import com.example.BancoDeDados.Repositores.ProfessorRepositores;
import com.example.BancoDeDados.Services.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

import static com.example.BancoDeDados.Model.GestaoInstitucional.*;

/**
 * Gestão da instituição (FE-301 a FE-309). O escopo é sempre a instituição de quem chama; cada rota exige uma
 * permissão da matriz (FE-306). Contrato em docs/instituicao-api.md do front.
 */
@RestController
public class InstituicaoGestaoController {

    public record PapelReq(String papel, List<String> permissoes) {}
    public record UsuarioPapelReq(String papel) {}
    public record UsuarioDTO(UUID id, String nome, String email, String papel) {}
    public record AlunoDTO(UUID id, String nome, String email) {}
    public record Minhas(String papel, UUID instituicaoId, List<String> permissoes) {}
    public record ArquivadaReq(Boolean arquivada) {}

    @Autowired private PermissaoService permissoes;
    @Autowired private AuditoriaService auditoria;
    @Autowired private ImportacaoService importacao;
    @Autowired private ConviteService convites;
    @Autowired private RelatoriosInstituicaoService relatorios;
    @Autowired private PeriodoTurmaService periodos;
    @Autowired private BoletimService boletins;
    @Autowired private ConfiguracaoInstituicaoService configuracao;
    @Autowired private ProfessorRepositores professorRepository;
    @Autowired private EstudanteRepositores estudanteRepository;
    @Autowired private InstituicaoRepository instituicaoRepository;

    private static ResponseEntity<byte[]> download(String nome, String tipo, byte[] dados) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nome, StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType(tipo)).body(dados);
    }

    private static byte[] bytes(MultipartFile f) {
        try {
            return f == null ? new byte[0] : f.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Não foi possível ler o arquivo");
        }
    }

    // ------------------------------------------------------------------ FE-306 papéis e permissões

    @GetMapping("/instituicao/minhas-permissoes")
    public Minhas minhas() {
        PermissaoService.Contexto c = permissoes.contexto();
        return new Minhas(c.papel(), c.instituicaoId(), permissoes.minhas());
    }

    @GetMapping("/instituicao/permissoes")
    public PermissaoService.Matriz matriz() {
        return permissoes.matriz(permissoes.exigirInstituicao(GERIR_PAPEIS));
    }

    @PutMapping("/instituicao/permissoes")
    public PermissaoService.Matriz salvarMatriz(@RequestBody PapelReq body) {
        UUID inst = permissoes.exigirInstituicao(GERIR_PAPEIS);
        var antes = permissoes.matriz(inst).matriz().get(body.papel());
        var depois = permissoes.salvar(inst, body.papel(), body.permissoes());
        auditoria.registrar(inst, "PERMISSOES_ALTERADAS", "Papel", body.papel(), "Permissões do papel " + body.papel() + " alteradas",
                antes == null ? null : String.join(", ", antes), String.join(", ", depois.matriz().get(body.papel())));
        return depois;
    }

    @GetMapping("/instituicao/usuarios")
    public List<UsuarioDTO> usuarios() {
        UUID inst = permissoes.exigirInstituicao(GERIR_PAPEIS);
        return professorRepository.findByInstituicaoId(inst).stream()
                .map(p -> new UsuarioDTO(p.getId(), p.getNome(), p.getEmail(), COORDENADOR.equals(p.getPapelInstituicao()) ? COORDENADOR : PROFESSOR))
                .sorted(Comparator.comparing(u -> u.nome() == null ? "" : u.nome().toLowerCase())).collect(Collectors.toList());
    }

    @PutMapping("/instituicao/usuarios/{professorId}/papel")
    public UsuarioDTO papelDoUsuario(@PathVariable UUID professorId, @RequestBody UsuarioPapelReq body) {
        UUID inst = permissoes.exigirInstituicao(GERIR_PAPEIS);
        if (!COORDENADOR.equals(body.papel()) && !PROFESSOR.equals(body.papel())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Papel inválido: use COORDENADOR ou PROFESSOR");
        }
        Professor p = professorRepository.findById(professorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado"));
        if (p.getInstituicao() == null || !p.getInstituicao().getId().equals(inst)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Usuário de outra instituição");
        }
        String antes = COORDENADOR.equals(p.getPapelInstituicao()) ? COORDENADOR : PROFESSOR;
        p.setPapelInstituicao(COORDENADOR.equals(body.papel()) ? COORDENADOR : null);
        professorRepository.save(p);
        if (!antes.equals(body.papel())) {
            auditoria.registrar(inst, "PAPEL_ALTERADO", "Usuario", p.getId(), "Papel de " + p.getNome() + " alterado", antes, body.papel());
        }
        return new UsuarioDTO(p.getId(), p.getNome(), p.getEmail(), body.papel());
    }

    // ------------------------------------------------------------------ FE-307 auditoria

    @GetMapping("/instituicao/auditoria")
    public AuditoriaService.Pagina auditoria(@RequestParam(required = false) UUID usuarioId,
                                             @RequestParam(required = false) String acao,
                                             @RequestParam(required = false) LocalDate inicio,
                                             @RequestParam(required = false) LocalDate fim,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        UUID inst = permissoes.exigirInstituicao(VER_AUDITORIA);
        ZoneId z = ZoneId.of("America/Sao_Paulo");
        Instant a = inicio == null ? null : inicio.atStartOfDay(z).toInstant();
        Instant b = fim == null ? null : fim.plusDays(1).atStartOfDay(z).toInstant();
        return auditoria.listar(inst, usuarioId, acao, a, b, page, size);
    }

    // ------------------------------------------------------------------ FE-301 importação

    @PostMapping(value = "/instituicao/importacao/previa", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportacaoService.Previa previa(@RequestParam String tipo, @RequestParam MultipartFile arquivo) {
        UUID inst = permissoes.exigirInstituicao(IMPORTAR_USUARIOS);
        return importacao.previa(inst, tipo, bytes(arquivo));
    }

    @PostMapping(value = "/instituicao/importacao/confirmar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportacaoService.Resultado confirmar(@RequestParam String tipo, @RequestParam MultipartFile arquivo) {
        UUID inst = permissoes.exigirInstituicao(IMPORTAR_USUARIOS);
        return importacao.confirmar(inst, tipo, bytes(arquivo));
    }

    // ------------------------------------------------------------------ FE-302 convites

    @GetMapping("/instituicao/convites")
    public List<ConviteService.ConviteDTO> convites() {
        return convites.listar(permissoes.exigirInstituicao(GERIR_CONVITES));
    }

    @PostMapping("/instituicao/convites")
    public ResponseEntity<ConviteService.ConviteDTO> convidar(@RequestBody ConviteService.CriarRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(convites.criar(permissoes.exigirInstituicao(GERIR_CONVITES), body));
    }

    @PostMapping("/instituicao/convites/{id}/reenviar")
    public ConviteService.ConviteDTO reenviar(@PathVariable UUID id) {
        return convites.reenviar(permissoes.exigirInstituicao(GERIR_CONVITES), id);
    }

    @DeleteMapping("/instituicao/convites/{id}")
    public ConviteService.ConviteDTO cancelar(@PathVariable UUID id) {
        return convites.cancelar(permissoes.exigirInstituicao(GERIR_CONVITES), id);
    }

    /** Público: o convidado ainda não tem conta. O token longo e aleatório é a credencial. */
    @GetMapping("/convites/{token}")
    public ConviteService.Info infoConvite(@PathVariable String token) {
        return convites.info(token);
    }

    @PostMapping("/convites/{token}/aceitar")
    public Map<String, String> aceitar(@PathVariable String token, @RequestBody ConviteService.AceiteRequest body) {
        return convites.aceitar(token, body);
    }

    // ------------------------------------------------------------------ FE-303 dashboard / FE-305 comparativo

    @GetMapping("/instituicao/dashboard")
    public RelatoriosInstituicaoService.Dashboard dashboard(@RequestParam(required = false) LocalDate inicio,
                                                            @RequestParam(required = false) LocalDate fim,
                                                            @RequestParam(required = false) UUID disciplinaId) {
        return relatorios.dashboard(permissoes.exigirInstituicao(VER_DASHBOARD), inicio, fim, disciplinaId);
    }

    @GetMapping("/instituicao/dashboard/detalhe")
    public RelatoriosInstituicaoService.Detalhe detalhe(@RequestParam String kpi,
                                                        @RequestParam(required = false) LocalDate inicio,
                                                        @RequestParam(required = false) LocalDate fim,
                                                        @RequestParam(required = false) UUID disciplinaId) {
        return relatorios.detalhe(permissoes.exigirInstituicao(VER_DASHBOARD), kpi, inicio, fim, disciplinaId);
    }

    @GetMapping("/instituicao/relatorios/comparativo")
    public RelatoriosInstituicaoService.Comparativo comparativo(@RequestParam String por,
                                                                @RequestParam(required = false) List<UUID> ids,
                                                                @RequestParam(required = false) LocalDate inicio,
                                                                @RequestParam(required = false) LocalDate fim) {
        return relatorios.comparar(permissoes.exigirInstituicao(VER_RELATORIOS), por, ids, inicio, fim);
    }

    @GetMapping("/instituicao/relatorios/comparativo/exportar")
    public ResponseEntity<byte[]> exportarComparativo(@RequestParam String por,
                                                      @RequestParam(required = false) List<UUID> ids,
                                                      @RequestParam(required = false) LocalDate inicio,
                                                      @RequestParam(required = false) LocalDate fim,
                                                      @RequestParam(defaultValue = "csv") String formato) throws IOException {
        UUID inst = permissoes.exigirInstituicao(VER_RELATORIOS);
        var c = relatorios.comparar(inst, por, ids, inicio, fim);
        List<String> cab = List.of(switch (c.por()) { case "TURMA" -> "Turma"; case "PROFESSOR" -> "Professor"; default -> "Disciplina"; },
                "Alunos", "Alunos ativos", "Média geral", "Entregas", "Entregas no prazo (%)");
        Locale br = Locale.forLanguageTag("pt-BR");
        List<List<String>> linhas = c.linhas().stream().map(l -> List.of(l.nome(), String.valueOf(l.alunos()), String.valueOf(l.alunosAtivos()),
                l.mediaGeral() == null ? "" : String.format(br, "%.1f", l.mediaGeral()), String.valueOf(l.entregas()),
                l.pctNoPrazo() == null ? "" : String.format(br, "%.1f", l.pctNoPrazo()))).collect(Collectors.toList());
        String base = "comparativo-" + c.por().toLowerCase() + "-" + c.periodo().inicio() + "-a-" + c.periodo().fim();
        auditoria.registrar(inst, "RELATORIO_COMPARATIVO_EXPORTADO", "Relatorio", null,
                "Comparativo por " + c.por().toLowerCase() + " exportado em " + formato.toLowerCase(), null, null);
        if ("pdf".equalsIgnoreCase(formato)) {
            Instituicao i = instituicaoRepository.findById(inst).orElseThrow();
            byte[] logo = configuracao.logo(inst).map(ConfiguracaoInstituicaoService.Logo::dados).orElse(null);
            return download(base + ".pdf", "application/pdf", ArquivosUtil.tabelaPdf("Comparativo por " + c.por().toLowerCase(),
                    List.of(i.getNome(), "Período: " + c.periodo().inicio() + " a " + c.periodo().fim()), logo, cab, linhas));
        }
        if (!"csv".equalsIgnoreCase(formato)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Formato inválido: use csv ou pdf");
        return download(base + ".csv", "text/csv; charset=UTF-8", ArquivosUtil.csv(cab, linhas));
    }

    // ------------------------------------------------------------------ FE-304 períodos e turmas

    @GetMapping("/instituicao/periodos")
    public List<PeriodoTurmaService.PeriodoDTO> periodos() {
        return periodos.periodos(permissoes.exigirInstituicao(GERIR_PERIODOS));
    }

    @PostMapping("/instituicao/periodos")
    public ResponseEntity<PeriodoTurmaService.PeriodoDTO> criarPeriodo(@RequestBody PeriodoTurmaService.PeriodoReq body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(periodos.criarPeriodo(permissoes.exigirInstituicao(GERIR_PERIODOS), body));
    }

    @PutMapping("/instituicao/periodos/{id}")
    public PeriodoTurmaService.PeriodoDTO editarPeriodo(@PathVariable UUID id, @RequestBody PeriodoTurmaService.PeriodoReq body) {
        return periodos.editarPeriodo(permissoes.exigirInstituicao(GERIR_PERIODOS), id, body);
    }

    @GetMapping("/instituicao/turmas")
    public List<PeriodoTurmaService.TurmaDTO> turmas(@RequestParam(required = false) UUID periodoId) {
        return periodos.turmas(permissoes.exigirInstituicao(GERIR_PERIODOS), periodoId);
    }

    @PostMapping("/instituicao/turmas")
    public ResponseEntity<PeriodoTurmaService.TurmaDTO> criarTurma(@RequestBody PeriodoTurmaService.TurmaReq body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(periodos.criarTurma(permissoes.exigirInstituicao(GERIR_PERIODOS), body));
    }

    @PutMapping("/instituicao/turmas/{id}")
    public PeriodoTurmaService.TurmaDTO editarTurma(@PathVariable UUID id, @RequestBody PeriodoTurmaService.TurmaReq body) {
        return periodos.editarTurma(permissoes.exigirInstituicao(GERIR_PERIODOS), id, body);
    }

    @PostMapping("/instituicao/turmas/rematricula")
    public PeriodoTurmaService.RematriculaResultado rematricula(@RequestBody PeriodoTurmaService.RematriculaReq body) {
        return periodos.rematricular(permissoes.exigirInstituicao(GERIR_PERIODOS), body);
    }

    @GetMapping("/instituicao/disciplinas-gestao")
    public List<PeriodoTurmaService.DisciplinaGestao> disciplinasGestao() {
        return periodos.disciplinas(permissoes.exigirInstituicao(GERIR_PERIODOS));
    }

    @PatchMapping("/instituicao/disciplinas/{id}/arquivar")
    public PeriodoTurmaService.DisciplinaGestao arquivar(@PathVariable UUID id, @RequestBody ArquivadaReq body) {
        return periodos.arquivar(permissoes.exigirInstituicao(GERIR_PERIODOS), id, Boolean.TRUE.equals(body.arquivada()));
    }

    @GetMapping("/instituicao/alunos")
    public List<AlunoDTO> alunos() {
        UUID id = permissoes.exigirInstituicao(GERIR_PERIODOS);
        Instituicao inst = instituicaoRepository.findById(id).orElseThrow();
        return estudanteRepository.findByInstituicao(inst.getNome()).stream()
                .sorted(Comparator.comparing(Estudante::getNome, String.CASE_INSENSITIVE_ORDER))
                .map(e -> new AlunoDTO(e.getId(), e.getNome(), e.getEmail())).collect(Collectors.toList());
    }

    // ------------------------------------------------------------------ FE-308 boletim e relatórios externos

    @GetMapping("/instituicao/boletim/turma/{turmaId}")
    public ResponseEntity<byte[]> boletimTurma(@PathVariable UUID turmaId) {
        var a = boletins.boletimTurma(permissoes.exigirInstituicao(EMITIR_BOLETINS), turmaId, null);
        return download(a.nome(), a.contentType(), a.dados());
    }

    @GetMapping("/instituicao/boletim/aluno/{estudanteId}")
    public ResponseEntity<byte[]> boletimAluno(@PathVariable UUID estudanteId, @RequestParam UUID periodoId) {
        var a = boletins.boletimAluno(permissoes.exigirInstituicao(EMITIR_BOLETINS), estudanteId, periodoId);
        return download(a.nome(), a.contentType(), a.dados());
    }

    @GetMapping("/instituicao/relatorios/risco-evasao")
    public ResponseEntity<byte[]> riscoEvasao(@RequestParam UUID turmaId, @RequestParam(defaultValue = "pdf") String formato) {
        var a = boletins.riscoTurma(permissoes.exigirInstituicao(EMITIR_BOLETINS), turmaId, formato);
        return download(a.nome(), a.contentType(), a.dados());
    }

    // ------------------------------------------------------------------ FE-309 configurações

    @GetMapping("/instituicao/configuracoes")
    public ConfiguracaoInstituicaoService.Config config() {
        return configuracao.obter(permissoes.exigirInstituicao(EDITAR_CONFIGURACOES));
    }

    @PutMapping("/instituicao/configuracoes")
    public ConfiguracaoInstituicaoService.Config salvarConfig(@RequestBody ConfiguracaoInstituicaoService.ConfigReq body) {
        return configuracao.salvar(permissoes.exigirInstituicao(EDITAR_CONFIGURACOES), body);
    }

    @PostMapping(value = "/instituicao/configuracoes/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ConfiguracaoInstituicaoService.Config enviarLogo(@RequestParam MultipartFile logo) {
        return configuracao.enviarLogo(permissoes.exigirInstituicao(EDITAR_CONFIGURACOES), logo);
    }

    @DeleteMapping("/instituicao/configuracoes/logo")
    public ConfiguracaoInstituicaoService.Config removerLogo() {
        return configuracao.removerLogo(permissoes.exigirInstituicao(EDITAR_CONFIGURACOES));
    }

    /** Tema e logo da instituição de quem está logado (aplicados pelo front via tokens). */
    @GetMapping("/branding/minha")
    public ConfiguracaoInstituicaoService.Branding minhaBranding() {
        return configuracao.minha();
    }

    /** O logo é público: aparece em tela de login/convite e em PDFs; não tem dado pessoal. */
    @GetMapping("/branding/{instituicaoId}/logo")
    public ResponseEntity<byte[]> logo(@PathVariable UUID instituicaoId) {
        return configuracao.logo(instituicaoId)
                .map(l -> ResponseEntity.ok().contentType(MediaType.parseMediaType(l.tipo()))
                        .header(HttpHeaders.CACHE_CONTROL, "public, max-age=3600").body(l.dados()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
