package com.example.BancoDeDados.Services;

import com.example.BancoDeDados.Model.*;
import com.example.BancoDeDados.Repositores.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/** FE-304: períodos letivos, turmas, rematrícula em lote e arquivamento de disciplinas. */
@Service
public class PeriodoTurmaService {

    public record PeriodoReq(String nome, LocalDate inicio, LocalDate fim) {}

    public record PeriodoDTO(UUID id, String nome, LocalDate inicio, LocalDate fim, int turmas, boolean vigente) {}

    public record Ref(UUID id, String nome) {}

    public record AlunoRef(UUID id, String nome, String email) {}

    public record TurmaReq(String nome, UUID periodoId, List<UUID> alunoIds, List<UUID> disciplinaIds) {}

    public record TurmaDTO(UUID id, String nome, UUID periodoId, String periodoNome, List<AlunoRef> alunos, List<Ref> disciplinas) {}

    public record RematriculaReq(List<UUID> turmaIds, UUID periodoDestinoId, String sufixoNome, List<UUID> excluirAlunoIds,
                                 List<UUID> disciplinaIds) {}

    public record RematriculaResultado(List<TurmaDTO> criadas, List<String> ignoradas, int alunosMatriculados) {}

    public record DisciplinaGestao(UUID id, String nome, String professor, int alunos, boolean arquivada) {}

    @Autowired private PeriodoRepository periodoRepository;
    @Autowired private TurmaRepository turmaRepository;
    @Autowired private InstituicaoRepository instituicaoRepository;
    @Autowired private EstudanteRepositores estudanteRepository;
    @Autowired private DisciplinaRepository disciplinaRepository;
    @Autowired private ConviteService conviteService;
    @Autowired private AuditoriaService auditoria;

    private static ResponseStatusException erro(HttpStatus s, String m) {
        return new ResponseStatusException(s, m);
    }

    private Instituicao inst(UUID id) {
        return instituicaoRepository.findById(id).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Instituição não encontrada"));
    }

    // ------------------------------------------------------------------ períodos

    private PeriodoDTO dto(Periodo p) {
        LocalDate hoje = LocalDate.now();
        return new PeriodoDTO(p.getId(), p.getNome(), p.getInicio(), p.getFim(), turmaRepository.findByPeriodoIdOrderByNomeAsc(p.getId()).size(),
                !hoje.isBefore(p.getInicio()) && !hoje.isAfter(p.getFim()));
    }

    private void validarPeriodo(PeriodoReq r) {
        if (r.nome() == null || r.nome().isBlank() || r.nome().length() > 80) {
            throw erro(HttpStatus.BAD_REQUEST, "Informe o nome do período (até 80 caracteres)");
        }
        if (r.inicio() == null || r.fim() == null) throw erro(HttpStatus.BAD_REQUEST, "Informe as datas de início e fim");
        if (r.fim().isBefore(r.inicio())) throw erro(HttpStatus.BAD_REQUEST, "O fim precisa ser depois do início");
    }

    @Transactional(readOnly = true)
    public List<PeriodoDTO> periodos(UUID instituicaoId) {
        return periodoRepository.findByInstituicaoIdOrderByInicioDesc(instituicaoId).stream().map(this::dto).collect(Collectors.toList());
    }

    @Transactional
    public PeriodoDTO criarPeriodo(UUID instituicaoId, PeriodoReq r) {
        validarPeriodo(r);
        Periodo p = new Periodo();
        p.setInstituicao(inst(instituicaoId));
        p.setNome(r.nome().trim());
        p.setInicio(r.inicio());
        p.setFim(r.fim());
        p = periodoRepository.save(p);
        auditoria.registrar(instituicaoId, "PERIODO_CRIADO", "Periodo", p.getId(),
                "Período " + p.getNome() + " (" + p.getInicio() + " a " + p.getFim() + ")", null, null);
        return dto(p);
    }

    private Periodo periodoDa(UUID instituicaoId, UUID id) {
        Periodo p = periodoRepository.findById(id).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Período não encontrado"));
        if (!p.getInstituicao().getId().equals(instituicaoId)) throw erro(HttpStatus.FORBIDDEN, "Período de outra instituição");
        return p;
    }

    @Transactional
    public PeriodoDTO editarPeriodo(UUID instituicaoId, UUID id, PeriodoReq r) {
        validarPeriodo(r);
        Periodo p = periodoDa(instituicaoId, id);
        String antes = p.getNome() + " (" + p.getInicio() + " a " + p.getFim() + ")";
        p.setNome(r.nome().trim());
        p.setInicio(r.inicio());
        p.setFim(r.fim());
        periodoRepository.save(p);
        auditoria.registrar(instituicaoId, "PERIODO_EDITADO", "Periodo", p.getId(), "Período editado", antes,
                p.getNome() + " (" + p.getInicio() + " a " + p.getFim() + ")");
        return dto(p);
    }

    // ------------------------------------------------------------------ turmas

    private TurmaDTO dto(Turma t) {
        return new TurmaDTO(t.getId(), t.getNome(), t.getPeriodo().getId(), t.getPeriodo().getNome(),
                t.getEstudantes().stream().map(e -> new AlunoRef(e.getId(), e.getNome(), e.getEmail()))
                        .sorted(Comparator.comparing(AlunoRef::nome, String.CASE_INSENSITIVE_ORDER)).collect(Collectors.toList()),
                t.getDisciplinas().stream().map(d -> new Ref(d.getId(), d.getNome())).collect(Collectors.toList()));
    }

    @Transactional(readOnly = true)
    public List<TurmaDTO> turmas(UUID instituicaoId, UUID periodoId) {
        List<Turma> ts = periodoId == null ? turmaRepository.findByInstituicaoIdOrderByNomeAsc(instituicaoId)
                : turmaRepository.findByPeriodoIdOrderByNomeAsc(periodoId).stream()
                .filter(t -> t.getInstituicao().getId().equals(instituicaoId)).collect(Collectors.toList());
        return ts.stream().map(this::dto).collect(Collectors.toList());
    }

    private List<Estudante> alunosDa(Instituicao inst, Collection<UUID> ids) {
        List<Estudante> out = new ArrayList<>();
        for (UUID id : new LinkedHashSet<>(ids == null ? List.<UUID>of() : ids)) {
            Estudante e = estudanteRepository.findById(id).orElseThrow(() -> erro(HttpStatus.BAD_REQUEST, "Aluno não encontrado"));
            if (!inst.getNome().equals(e.getInstituicao())) throw erro(HttpStatus.FORBIDDEN, "O aluno " + e.getNome() + " é de outra instituição");
            out.add(e);
        }
        return out;
    }

    private List<Disciplina> disciplinasDa(Instituicao inst, Collection<UUID> ids) {
        List<Disciplina> out = new ArrayList<>();
        for (UUID id : new LinkedHashSet<>(ids == null ? List.<UUID>of() : ids)) {
            Disciplina d = disciplinaRepository.findById(id).orElseThrow(() -> erro(HttpStatus.BAD_REQUEST, "Disciplina não encontrada"));
            if (d.getInstituicao() == null || !d.getInstituicao().getId().equals(inst.getId())) {
                throw erro(HttpStatus.FORBIDDEN, "A disciplina " + d.getNome() + " é de outra instituição");
            }
            d.exigirAtiva();
            out.add(d);
        }
        return out;
    }

    private void matricular(Turma t) {
        for (Disciplina d : t.getDisciplinas()) {
            for (Estudante e : t.getEstudantes()) conviteService.vincularAluno(d, e);
        }
    }

    private void validarTurma(TurmaReq r) {
        if (r.nome() == null || r.nome().isBlank() || r.nome().length() > 80) {
            throw erro(HttpStatus.BAD_REQUEST, "Informe o nome da turma (até 80 caracteres)");
        }
    }

    @Transactional
    public TurmaDTO criarTurma(UUID instituicaoId, TurmaReq r) {
        validarTurma(r);
        Instituicao inst = inst(instituicaoId);
        if (r.periodoId() == null) throw erro(HttpStatus.BAD_REQUEST, "Escolha o período da turma");
        Turma t = new Turma();
        t.setInstituicao(inst);
        t.setPeriodo(periodoDa(instituicaoId, r.periodoId()));
        t.setNome(r.nome().trim());
        t.setEstudantes(alunosDa(inst, r.alunoIds()));
        t.setDisciplinas(disciplinasDa(inst, r.disciplinaIds()));
        t = turmaRepository.save(t);
        matricular(t);
        auditoria.registrar(instituicaoId, "TURMA_CRIADA", "Turma", t.getId(),
                "Turma " + t.getNome() + " em " + t.getPeriodo().getNome() + " com " + t.getEstudantes().size() + " aluno(s)", null, null);
        return dto(t);
    }

    @Transactional
    public TurmaDTO editarTurma(UUID instituicaoId, UUID id, TurmaReq r) {
        validarTurma(r);
        Instituicao inst = inst(instituicaoId);
        Turma t = turmaRepository.findById(id).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Turma não encontrada"));
        if (!t.getInstituicao().getId().equals(instituicaoId)) throw erro(HttpStatus.FORBIDDEN, "Turma de outra instituição");
        String antes = t.getNome() + ": " + t.getEstudantes().size() + " aluno(s), " + t.getDisciplinas().size() + " disciplina(s)";
        t.setNome(r.nome().trim());
        if (r.periodoId() != null) t.setPeriodo(periodoDa(instituicaoId, r.periodoId()));
        if (r.alunoIds() != null) t.setEstudantes(alunosDa(inst, r.alunoIds()));
        if (r.disciplinaIds() != null) t.setDisciplinas(disciplinasDa(inst, r.disciplinaIds()));
        turmaRepository.save(t);
        matricular(t);
        auditoria.registrar(instituicaoId, "TURMA_EDITADA", "Turma", t.getId(), "Turma editada", antes,
                t.getNome() + ": " + t.getEstudantes().size() + " aluno(s), " + t.getDisciplinas().size() + " disciplina(s)");
        return dto(t);
    }

    /** Cria, no período de destino, uma turma nova para cada turma de origem, com os mesmos alunos (menos os excluídos). */
    @Transactional
    public RematriculaResultado rematricular(UUID instituicaoId, RematriculaReq r) {
        Instituicao inst = inst(instituicaoId);
        if (r.turmaIds() == null || r.turmaIds().isEmpty()) throw erro(HttpStatus.BAD_REQUEST, "Escolha ao menos uma turma");
        if (r.periodoDestinoId() == null) throw erro(HttpStatus.BAD_REQUEST, "Escolha o período de destino");
        Periodo destino = periodoDa(instituicaoId, r.periodoDestinoId());
        Set<UUID> fora = new HashSet<>(r.excluirAlunoIds() == null ? List.<UUID>of() : r.excluirAlunoIds());
        List<Disciplina> discOverride = r.disciplinaIds() == null ? null : disciplinasDa(inst, r.disciplinaIds());
        String sufixo = r.sufixoNome() == null ? "" : r.sufixoNome().trim();

        List<TurmaDTO> criadas = new ArrayList<>();
        List<String> ignoradas = new ArrayList<>();
        int total = 0;
        Set<String> nomesNoDestino = turmaRepository.findByPeriodoIdOrderByNomeAsc(destino.getId()).stream()
                .map(t -> t.getNome().toLowerCase()).collect(Collectors.toCollection(HashSet::new));
        for (UUID id : new LinkedHashSet<>(r.turmaIds())) {
            Turma o = turmaRepository.findById(id).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Turma não encontrada"));
            if (!o.getInstituicao().getId().equals(instituicaoId)) throw erro(HttpStatus.FORBIDDEN, "Turma de outra instituição");
            if (o.getPeriodo().getId().equals(destino.getId())) {
                throw erro(HttpStatus.BAD_REQUEST, "O período de destino precisa ser diferente do da turma " + o.getNome());
            }
            String nome = (o.getNome() + (sufixo.isEmpty() ? "" : " " + sufixo)).trim();
            if (nome.length() > 80) nome = nome.substring(0, 80);
            if (!nomesNoDestino.add(nome.toLowerCase())) {
                ignoradas.add(nome + " (já existe no período de destino)");
                continue;
            }
            Turma n = new Turma();
            n.setInstituicao(inst);
            n.setPeriodo(destino);
            n.setNome(nome);
            n.setEstudantes(o.getEstudantes().stream().filter(e -> !fora.contains(e.getId())).collect(Collectors.toCollection(ArrayList::new)));
            n.setDisciplinas(discOverride != null ? new ArrayList<>(discOverride)
                    : o.getDisciplinas().stream().filter(d -> !d.isArquivada()).collect(Collectors.toCollection(ArrayList::new)));
            n = turmaRepository.save(n);
            matricular(n);
            total += n.getEstudantes().size();
            criadas.add(dto(n));
        }
        auditoria.registrar(instituicaoId, "REMATRICULA_EM_LOTE", "Turma", null,
                criadas.size() + " turma(s) rematriculada(s) em " + destino.getNome() + " (" + total + " alunos)", null, null);
        return new RematriculaResultado(criadas, ignoradas, total);
    }

    // ------------------------------------------------------------------ disciplinas

    @Transactional(readOnly = true)
    public List<DisciplinaGestao> disciplinas(UUID instituicaoId) {
        return disciplinaRepository.findByInstituicao_Id(instituicaoId).stream()
                .map(d -> new DisciplinaGestao(d.getId(), d.getNome(), d.getProfessor() == null ? null : d.getProfessor().getNome(),
                        d.getEstudantes().size(), d.isArquivada()))
                .sorted(Comparator.comparing(DisciplinaGestao::arquivada).thenComparing(DisciplinaGestao::nome, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }

    @Transactional
    public DisciplinaGestao arquivar(UUID instituicaoId, UUID disciplinaId, boolean arquivada) {
        Disciplina d = disciplinaRepository.findById(disciplinaId).orElseThrow(() -> erro(HttpStatus.NOT_FOUND, "Disciplina não encontrada"));
        if (d.getInstituicao() == null || !d.getInstituicao().getId().equals(instituicaoId)) {
            throw erro(HttpStatus.FORBIDDEN, "Disciplina de outra instituição");
        }
        boolean antes = d.isArquivada();
        d.setArquivada(arquivada);
        disciplinaRepository.save(d);
        if (antes != arquivada) {
            auditoria.registrar(instituicaoId, arquivada ? "DISCIPLINA_ARQUIVADA" : "DISCIPLINA_DESARQUIVADA", "Disciplina", d.getId(),
                    d.getNome(), antes ? "arquivada" : "ativa", arquivada ? "arquivada" : "ativa");
        }
        return new DisciplinaGestao(d.getId(), d.getNome(), d.getProfessor() == null ? null : d.getProfessor().getNome(),
                d.getEstudantes().size(), d.isArquivada());
    }
}
