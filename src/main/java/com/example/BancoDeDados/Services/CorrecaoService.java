package com.example.BancoDeDados.Services;

import com.example.BancoDeDados.Model.*;
import com.example.BancoDeDados.Repositores.ListaEstudanteNotaRepository;
import com.example.BancoDeDados.Repositores.RespostaEstudantesRepository;
import com.example.BancoDeDados.dto.QuestaoComAlternativasDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/** FE-201: fila de correção das respostas abertas. */
@Service
public class CorrecaoService {

    public record Pendente(Long respostaId, UUID listaId, String listaTitulo, UUID disciplinaId, String disciplinaNome,
                           Integer questaoId, String enunciado, UUID estudanteId, String estudanteNome,
                           Instant enviadaEm) {}

    public record Opcao(UUID id, String nome) {}

    public record Fila(long total, List<Pendente> items, List<Opcao> listas, List<Opcao> disciplinas) {}

    public record Detalhe(Long respostaId, UUID listaId, String listaTitulo, UUID disciplinaId, String disciplinaNome,
                          Integer questaoId, String cabecalho, String enunciado,
                          List<QuestaoComAlternativasDTO.ImagemDTO> imagens, String assunto, String rubrica,
                          UUID estudanteId, String estudanteNome, String textoResposta,
                          BigDecimal notaQuestao, String feedback, boolean corrigida, Instant enviadaEm,
                          long restantes) {}

    public record Resultado(Long respostaId, long restantes, boolean notaLancada) {}

    @Autowired
    private RespostaEstudantesRepository respostaRepository;
    @Autowired
    private ListaEstudanteNotaRepository notaRepository;
    @Autowired
    private NotaListaService notaListaService;
    @Autowired
    private NotificacaoService notificacaoService;
    @Autowired
    private QuestaoOtimizadoService questaoOtimizadoService;
    @Autowired
    private AuditoriaService auditoria;

    /** Pendentes do professor (null = sem escopo, para instituição) de alunos que já finalizaram a lista. */
    private List<RespostaEstudantes> pendentesDoEscopo(UUID professorId) {
        Map<UUID, Set<UUID>> finalizados = new HashMap<>(); // listaId -> estudantes que finalizaram
        List<RespostaEstudantes> out = new ArrayList<>();
        for (RespostaEstudantes r : respostaRepository.findPendentesCorrecao()) {
            Lista l = r.getQuestao().getLista();
            if (professorId != null && (l.getProfessor() == null || !professorId.equals(l.getProfessor().getId()))) continue;
            Set<UUID> fin = finalizados.computeIfAbsent(l.getId(), id -> notaRepository.findByListaId(id).stream()
                    .filter(ListaEstudanteNota::isFinalizada)
                    .map(n -> n.getEstudante().getId())
                    .collect(Collectors.toSet()));
            if (fin.contains(r.getEstudante().getId())) out.add(r);
        }
        out.sort(Comparator.comparing((RespostaEstudantes r) -> r.getAtualizadoEm() == null ? Instant.EPOCH : r.getAtualizadoEm())
                .thenComparing(RespostaEstudantes::getId));
        return out;
    }

    private static Pendente paraPendente(RespostaEstudantes r) {
        Lista l = r.getQuestao().getLista();
        Disciplina d = l.getDisciplina();
        String enun = r.getQuestao().getEnunciado();
        return new Pendente(r.getId(), l.getId(), l.getTitulo(), d == null ? null : d.getId(),
                d == null ? null : d.getNome(), r.getQuestao().getId(),
                enun != null && enun.length() > 160 ? enun.substring(0, 159) + "…" : enun,
                r.getEstudante().getId(), r.getEstudante().getNome(), r.getAtualizadoEm());
    }

    @Transactional(readOnly = true)
    public Fila listar(UUID professorId, UUID listaId, UUID disciplinaId, String busca) {
        List<RespostaEstudantes> todas = pendentesDoEscopo(professorId);

        Map<UUID, Opcao> listas = new LinkedHashMap<>();
        Map<UUID, Opcao> disciplinas = new LinkedHashMap<>();
        for (RespostaEstudantes r : todas) {
            Lista l = r.getQuestao().getLista();
            listas.putIfAbsent(l.getId(), new Opcao(l.getId(), l.getTitulo()));
            if (l.getDisciplina() != null) {
                disciplinas.putIfAbsent(l.getDisciplina().getId(), new Opcao(l.getDisciplina().getId(), l.getDisciplina().getNome()));
            }
        }
        String q = busca == null ? "" : busca.trim().toLowerCase();
        List<Pendente> items = todas.stream()
                .filter(r -> listaId == null || listaId.equals(r.getQuestao().getLista().getId()))
                .filter(r -> disciplinaId == null || (r.getQuestao().getLista().getDisciplina() != null
                        && disciplinaId.equals(r.getQuestao().getLista().getDisciplina().getId())))
                .filter(r -> q.isEmpty() || r.getEstudante().getNome().toLowerCase().contains(q))
                .map(CorrecaoService::paraPendente)
                .collect(Collectors.toList());
        return new Fila(items.size(), items, new ArrayList<>(listas.values()), new ArrayList<>(disciplinas.values()));
    }

    private RespostaEstudantes carregarAberta(Long id, UUID professorId) {
        RespostaEstudantes r = respostaRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resposta não encontrada"));
        if (!r.getQuestao().isAberta()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Esta resposta é de uma questão objetiva");
        }
        Lista l = r.getQuestao().getLista();
        if (professorId != null && (l.getProfessor() == null || !professorId.equals(l.getProfessor().getId()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Esta resposta pertence a outro professor");
        }
        return r;
    }

    @Transactional(readOnly = true)
    public Detalhe detalhe(Long id, UUID professorId) {
        RespostaEstudantes r = carregarAberta(id, professorId);
        Questao q = r.getQuestao();
        Lista l = q.getLista();
        Disciplina d = l.getDisciplina();
        var imagens = questaoOtimizadoService.buscarQuestaoComAlternativasPorId(q.getId())
                .map(QuestaoComAlternativasDTO::getImagens).orElse(List.of());
        return new Detalhe(r.getId(), l.getId(), l.getTitulo(), d == null ? null : d.getId(), d == null ? null : d.getNome(),
                q.getId(), q.getCabecalho(), q.getEnunciado(), imagens, q.getAssunto(), q.getRubrica(),
                r.getEstudante().getId(), r.getEstudante().getNome(), r.getTextoResposta(),
                r.getNotaQuestao(), r.getFeedback(), r.getCorrigidaEm() != null, r.getAtualizadoEm(),
                pendentesDoEscopo(professorId).size());
    }

    @Transactional
    public Resultado corrigir(Long id, UUID professorId, BigDecimal nota, String feedback) {
        if (nota == null || nota.compareTo(BigDecimal.ZERO) < 0 || nota.compareTo(BigDecimal.TEN) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A nota deve estar entre 0 e 10");
        }
        if (feedback != null && feedback.length() > 2000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O feedback pode ter no máximo 2000 caracteres");
        }
        RespostaEstudantes r = carregarAberta(id, professorId);
        if (r.getQuestao().getLista().getDisciplina() != null) r.getQuestao().getLista().getDisciplina().exigirAtiva();
        boolean primeiraCorrecao = r.getCorrigidaEm() == null;
        BigDecimal notaAnterior = r.getNotaQuestao();
        r.setNotaQuestao(nota.setScale(2, java.math.RoundingMode.HALF_UP));
        r.setFeedback(feedback == null || feedback.isBlank() ? null : feedback.trim());
        r.setCorrigidaEm(Instant.now());
        r.setResposta(r.isCorreta());
        respostaRepository.save(r);

        Lista lista = r.getQuestao().getLista();
        UUID estudanteId = r.getEstudante().getId();
        if (lista.getProfessor() != null && lista.getProfessor().getInstituicao() != null) {
            auditoria.registrar(lista.getProfessor().getInstituicao().getId(),
                    primeiraCorrecao ? "NOTA_LANCADA" : "NOTA_ALTERADA", "Resposta", r.getId(),
                    "Questão aberta de " + r.getEstudante().getNome() + " em \"" + lista.getTitulo() + "\"",
                    notaAnterior == null ? "sem nota" : notaAnterior.toPlainString(), r.getNotaQuestao().toPlainString());
        }
        ListaEstudanteNota notaLista = notaListaService.calcularESalvarNota(lista.getId(), estudanteId);

        // Notifica o aluno quando a última resposta aberta da lista é corrigida (nota final lançada)
        boolean lancada = false;
        if (primeiraCorrecao && notaLista.isFinalizada()
                && notaListaService.contarPendentesCorrecao(lista.getId(), estudanteId) == 0) {
            String link = lista.isGabaritoLiberado() ? "/listas/" + lista.getId() + "/revisao"
                    : "/listas/" + lista.getId() + "/questoes";
            notificacaoService.criar(estudanteId, TipoNotificacao.NOTA_LANCADA, "Nota lançada",
                    lista.getTitulo() + ": a correção das questões abertas foi concluída.", link);
            lancada = true;
        }
        return new Resultado(r.getId(), pendentesDoEscopo(professorId).size(), lancada);
    }
}
