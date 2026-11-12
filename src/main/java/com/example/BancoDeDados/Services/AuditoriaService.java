package com.example.BancoDeDados.Services;

import com.example.BancoDeDados.Model.EventoAuditoria;
import com.example.BancoDeDados.Repositores.EventoAuditoriaRepository;
import com.example.BancoDeDados.Repositores.InstituicaoRepository;
import com.example.BancoDeDados.Repositores.ProfessorRepositores;
import com.example.BancoDeDados.Security.CallerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/** FE-307: registra e consulta o log de auditoria da instituição. Registrar nunca derruba a operação principal. */
@Service
public class AuditoriaService {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaService.class);

    public record Evento(UUID id, Instant criadoEm, UUID usuarioId, String usuarioNome, String acao, String entidade,
                         String entidadeId, String descricao, String valorAnterior, String valorNovo) {}

    public record Pagina(List<Evento> items, int page, int size, long totalElements, int totalPages, List<String> acoes,
                         List<Usuario> usuarios) {}

    public record Usuario(UUID id, String nome) {}

    @Autowired
    private EventoAuditoriaRepository repository;
    @Autowired
    private InstituicaoRepository instituicaoRepository;
    @Autowired
    private ProfessorRepositores professorRepository;

    private static String cortar(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    /** Registra um evento em nome do chamador atual (token da requisição). */
    @Transactional
    public void registrar(UUID instituicaoId, String acao, String entidade, Object entidadeId, String descricao,
                          String anterior, String novo) {
        if (instituicaoId == null) return;
        try {
            EventoAuditoria e = new EventoAuditoria();
            e.setInstituicaoId(instituicaoId);
            UUID uid = CallerContext.userId();
            e.setUsuarioId(uid);
            e.setUsuarioNome(nomeDoUsuario(uid));
            e.setAcao(acao);
            e.setEntidade(entidade);
            e.setEntidadeId(entidadeId == null ? null : entidadeId.toString());
            e.setDescricao(cortar(descricao, 500));
            e.setValorAnterior(cortar(anterior, 1000));
            e.setValorNovo(cortar(novo, 1000));
            repository.save(e);
        } catch (RuntimeException ex) {
            log.warn("Não foi possível registrar auditoria {}: {}", acao, ex.getMessage());
        }
    }

    private String nomeDoUsuario(UUID uid) {
        if (uid == null) return "Sistema";
        if (CallerContext.instituicaoIdOuNull() != null) {
            return instituicaoRepository.findById(uid).map(i -> i.getNome() + " (admin)").orElse("Admin");
        }
        if (CallerContext.isProfessor()) return professorRepository.findById(uid).map(p -> p.getNome()).orElse("Professor");
        if (CallerContext.isAluno()) return "Aluno";
        return "Usuário";
    }

    @Transactional(readOnly = true)
    public Pagina listar(UUID instituicaoId, UUID usuarioId, String acao, Instant inicio, Instant fim, int page, int size) {
        List<EventoAuditoria> todos = repository.findByInstituicaoIdOrderByCriadoEmDesc(instituicaoId);
        List<String> acoes = todos.stream().map(EventoAuditoria::getAcao).distinct().sorted().collect(Collectors.toList());
        java.util.Map<UUID, String> nomes = new java.util.LinkedHashMap<>();
        for (EventoAuditoria e : todos) if (e.getUsuarioId() != null) nomes.putIfAbsent(e.getUsuarioId(), e.getUsuarioNome());
        List<Usuario> usuarios = nomes.entrySet().stream().map(en -> new Usuario(en.getKey(), en.getValue())).collect(Collectors.toList());
        List<EventoAuditoria> filtrados = todos.stream()
                .filter(e -> usuarioId == null || usuarioId.equals(e.getUsuarioId()))
                .filter(e -> acao == null || acao.isBlank() || acao.equals(e.getAcao()))
                .filter(e -> inicio == null || !e.getCriadoEm().isBefore(inicio))
                .filter(e -> fim == null || e.getCriadoEm().isBefore(fim))
                .collect(Collectors.toList());
        int s = Math.min(Math.max(size, 1), 100);
        int p = Math.max(page, 0);
        int from = Math.min(p * s, filtrados.size());
        int to = Math.min(from + s, filtrados.size());
        List<Evento> items = filtrados.subList(from, to).stream().map(e -> new Evento(e.getId(), e.getCriadoEm(),
                e.getUsuarioId(), e.getUsuarioNome(), e.getAcao(), e.getEntidade(), e.getEntidadeId(), e.getDescricao(),
                e.getValorAnterior(), e.getValorNovo())).collect(Collectors.toList());
        return new Pagina(items, p, s, filtrados.size(), (int) Math.ceil(filtrados.size() / (double) s), acoes, usuarios);
    }
}
