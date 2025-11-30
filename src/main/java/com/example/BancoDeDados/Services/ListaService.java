package com.example.BancoDeDados.Services;

import com.example.BancoDeDados.Model.*;
import com.example.BancoDeDados.Repositores.*;
import com.example.BancoDeDados.ResponseDTO.*;
import com.example.BancoDeDados.Repositores.projections.PiorListaProjection;
import com.example.BancoDeDados.Repositores.projections.QuestaoStatsProjection;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

@Service
@Transactional
public class ListaService {

    @Autowired
    private ListaRepository listaRepository;

    @Autowired
    private DisciplinaRepository disciplinaRepository;

    @Autowired
    private EstudanteRepositores estudanteRepositores;

    @Autowired
    private QuestaoRepositores questaoRepository;

    @Autowired
    private ProfessorRepositores professorRepository;

    @Autowired
    private ListaEstudanteNotaRepository listaEstudanteNotaRepository;

    @Autowired
    private QuestaoOtimizadoService questaoOtimizadoService;

    @Autowired
    private RespostaEstudantesRepository respostaEstudantesRepository;

    public List<AlunoNotaDTO> getNotasByListaId(UUID listaId) {
        List<ListaEstudanteNota> notas = listaEstudanteNotaRepository.findByListaId(listaId);
        return notas.stream()
                .map(nota -> new AlunoNotaDTO(nota.getEstudante().getNome(), nota.getNota()))
                .collect(Collectors.toList());
    }

    // Adicione este método no ListaService.java

    // No ListaService.java - Método corrigido
    @Transactional(readOnly = true)
    public List<QuestaoResponseDTO> buscarQuestoesPorEstudante(UUID estudanteId) {
        // 1. Buscar todas as listas associadas ao estudante
        List<Lista> listasDoEstudante = listaRepository.findByEstudanteId(estudanteId);

        if (listasDoEstudante.isEmpty()) {
            throw new RuntimeException("Estudante não está associado a nenhuma lista");
        }

        // 2. Coletar todas as questões de todas as listas
        List<QuestaoResponseDTO> todasQuestoes = new ArrayList<>();

        for (Lista lista : listasDoEstudante) {
            // Buscar questões da lista
            List<Questao> questoesDaLista = questaoRepository.findByListaIdWithAlternativas(lista.getId());

            if (questoesDaLista != null && !questoesDaLista.isEmpty()) {
                // Inicializar alternativas DENTRO da transação antes de converter para DTO
                for (Questao questao : questoesDaLista) {
                    // Força o carregamento das alternativas enquanto ainda está na transação
                    if (questao.getAlternativas() != null) {
                        questao.getAlternativas().size(); // Toca a coleção lazy para inicializar
                    }
                }

                // Agora converte para DTO com alternativas já carregadas
                List<QuestaoResponseDTO> questoesDTOs = questoesDaLista.stream()
                        .map(questao -> new QuestaoResponseDTO(
                                questao.getId(),
                                questao.getCabecalho(),
                                questao.getEnunciado(),
                                questao.getAlternativasTexto(),
                                questao.getGabarito()
                        ))
                        .collect(Collectors.toList());

                todasQuestoes.addAll(questoesDTOs);
            }
        }

        if (todasQuestoes.isEmpty()) {
            throw new RuntimeException("Nenhuma questão encontrada para o estudante");
        }

        return todasQuestoes;
    }
    @Transactional
    public void salvarQuestoesComLista(List<Questao> questoes, UUID listaId) {
        // Buscar a lista
        Lista lista = listaRepository.findById(listaId)
                .orElseThrow(() -> new RuntimeException("Lista não encontrada com ID: " + listaId));

        System.out.println("Lista encontrada: " + lista.getTitulo());
        System.out.println("Número de questões a serem adicionadas: " + questoes.size());

        // CORREÇÃO: Usar o método adicionarQuestao para estabelecer relação bidirecional
        for (Questao questao : questoes) {
            // Define a lista na questão
            questao.setLista(lista);

            // Adiciona a questão à lista usando o método que estabelece a relação bidirecional
            lista.adicionarQuestao(questao);

            // Salva a questão
            Questao questaoSalva = questaoRepository.save(questao);
            System.out.println("Questão salva com ID: " + questaoSalva.getId());
        }

        // CORREÇÃO: Salvar a lista para garantir que as alterações são persistidas
        Lista listaAtualizada = listaRepository.save(lista);

        // VERIFICAÇÃO: Buscar a lista novamente para confirmar
        Lista listaVerificada = listaRepository.findByIdWithQuestoes(listaId)
                .orElseThrow(() -> new RuntimeException("Lista não encontrada após salvar"));

        System.out.println("Número final de questões na lista: " +
                (listaVerificada.getQuestoes() != null ? listaVerificada.getQuestoes().size() : 0));
    }

    // Métodos para Questões
    @CacheEvict(value = {"listaQuestoesCompact"}, allEntries = true)
    public ListaResponseDTO adicionarQuestao(UUID listaId, Integer questaoId) {
        Lista lista = listaRepository.findById(listaId)
                .orElseThrow(() -> new RuntimeException("Lista não encontrada"));

        // Usando método otimizado com FETCH JOIN
        Questao questao = questaoRepository.findByIdWithAlternativas(questaoId)
                .orElseThrow(() -> new RuntimeException("Questão não encontrada"));

        // Verifica se a questão já está na lista
        if (lista.getQuestoes() != null && lista.getQuestoes().contains(questao)) {
            throw new RuntimeException("Questão já está na lista");
        }

        // Ajusta relação bidirecional: lado dono é Questao (ManyToOne)
        questao.setLista(lista);
        if (lista.getQuestoes() == null) {
            lista.setQuestoes(new ArrayList<>());
        }
        lista.getQuestoes().add(questao);

        // Persistir pelo lado dono
        questaoRepository.save(questao);
        listaRepository.save(lista);

        return convertToDTO(lista);
    }
    public ListaResponseDTO criarListaComDisciplina(String titulo, UUID professorId, UUID disciplinaId) {
        // 1. Busca professor
        Professor professor = professorRepository.findById(professorId)
                .orElseThrow(() -> new RuntimeException("Professor não encontrado"));

        // 2. Busca disciplina
        Disciplina disciplina = disciplinaRepository.findById(disciplinaId)
                .orElseThrow(() -> new RuntimeException("Disciplina não encontrada"));

        // 3. Verifica se a disciplina pertence ao professor
        if (!disciplina.getProfessor().getId().equals(professorId)) {
            throw new RuntimeException("Disciplina não pertence ao professor");
        }

        // 4. Cria a lista com estudantes inicializados
        Lista lista = new Lista();
        lista.setTitulo(titulo);
        lista.setDisciplina(disciplina);
        lista.setProfessor(professor);

        // 5. Inicializa os estudantes da lista com os estudantes da disciplina
        if (disciplina.getEstudantes() != null && !disciplina.getEstudantes().isEmpty()) {
            lista.setEstudantes(new ArrayList<>(disciplina.getEstudantes()));
        } else {
            lista.setEstudantes(new ArrayList<>());
        }

        // 6. Salva e retorna DTO
        Lista listaSalva = listaRepository.save(lista);
        return convertToDTO(listaSalva);
    }

    /**
     * Método auxiliar para adicionar estudantes da disciplina à lista
     */
    private void adicionarEstudantesDaDisciplina(UUID listaId, UUID disciplinaId) {
        Lista lista = listaRepository.findById(listaId)
                .orElseThrow(() -> new RuntimeException("Lista não encontrada"));

        // Busca a disciplina e obtém os estudantes diretamente
        Disciplina disciplina = disciplinaRepository.findById(disciplinaId)
                .orElseThrow(() -> new RuntimeException("Disciplina não encontrada"));

        // Adiciona cada estudante à lista
        for (Estudante estudante : disciplina.getEstudantes()) {
            try {
                if (!lista.getEstudantes().contains(estudante)) {
                    lista.getEstudantes().add(estudante);
                    System.out.println("Estudante " + estudante.getNome() + " adicionado à lista");
                }
            } catch (Exception e) {
                System.err.println("Erro ao adicionar estudante " + estudante.getNome() + ": " + e.getMessage());
            }
        }

        listaRepository.save(lista);
        System.out.println("Total de estudantes na lista após adição: " + lista.getEstudantes().size());
    }

    /**
     * Busca listas por disciplina - baseado no professor que leciona a disciplina
     */
    public List<ListaResponseDTO> buscarListasPorDisciplina(UUID disciplinaId) {
        // Busca a disciplina para obter o professor
        Disciplina disciplina = disciplinaRepository.findById(disciplinaId)
                .orElseThrow(() -> new RuntimeException("Disciplina não encontrada"));

        UUID professorId = disciplina.getProfessor().getId();

        // Busca todas as listas do professor da disciplina
        List<Lista> listasDoProfessor = listaRepository.findByProfessorId(professorId);

        return listasDoProfessor.stream()
                .map(this::convertToDTO)
                .collect(toList());
    }

    public List<ListaResponseDTO> buscarListasPorEstudante(UUID estudanteId) {
        List<Lista> listas = listaRepository.findByEstudanteId(estudanteId);
        return listas.stream()
                .map(this::convertToDTO)
                .collect(toList());
    }
    public List<DisciplinaProfessorResponseDTO> buscarDisciplinasPorProfessor(UUID professorId) {
        List<Disciplina> disciplinas = disciplinaRepository.findByProfessorId(professorId);

        return disciplinas.stream()
                .map(this::convertToDisciplinaDTO)
                .collect(toList());
    }

    /**
     * Converte Disciplina para DisciplinaProfessorResponseDTO
     */
    private DisciplinaProfessorResponseDTO convertToDisciplinaDTO(Disciplina disciplina) {
        return new DisciplinaProfessorResponseDTO(
                disciplina.getId(),
                disciplina.getNome(),
                disciplina.getProfessor().getNome(),
                disciplina.getInstituicao().getNome()
        );
    }

    /**
     * Associa uma lista existente a uma disciplina
     * Verifica se o professor da lista é o mesmo da disciplina
     * E adiciona os estudantes da disciplina à lista
     */
    public ListaResponseDTO associarListaExistenteADisciplina(UUID listaId, UUID disciplinaId) {
        Lista lista = listaRepository.findById(listaId)
                .orElseThrow(() -> new RuntimeException("Lista não encontrada"));

        // Verifica se a disciplina existe
        Disciplina disciplina = disciplinaRepository.findById(disciplinaId)
                .orElseThrow(() -> new RuntimeException("Disciplina não encontrada"));

        // Verifica se o professor da lista é o mesmo da disciplina
        if (!lista.getProfessor().getId().equals(disciplina.getProfessor().getId())) {
            throw new RuntimeException("O professor da lista não é o mesmo da disciplina");
        }

        // Adiciona os estudantes da disciplina à lista
        adicionarEstudantesDaDisciplina(listaId, disciplinaId);

        return convertToDTO(lista);
    }

    @CacheEvict(value = {"listaQuestoesCompact"}, allEntries = true)
    @Transactional
    public ListaResponseDTO adicionarQuestaoExistente(UUID listaId, Integer questaoId) {
        Lista lista = listaRepository.findByIdWithQuestoes(listaId)
                .orElseThrow(() -> new RuntimeException("Lista não encontrada"));

        // Usando método otimizado com FETCH JOIN
        Questao questao = questaoRepository.findByIdWithAlternativas(questaoId)
                .orElseThrow(() -> new RuntimeException("Questão não encontrada"));

        if (lista.getQuestoes() != null && lista.getQuestoes().contains(questao)) {
            throw new RuntimeException("Questão já está na lista");
        }

        // Ajusta relação bidirecional
        questao.setLista(lista);
        if (lista.getQuestoes() == null) {
            lista.setQuestoes(new ArrayList<>());
        }
        lista.getQuestoes().add(questao);

        questaoRepository.save(questao);
        listaRepository.save(lista);

        return new ListaResponseDTO(lista.getId(), lista.getTitulo(), lista.getProfessor().getNome());
    }

    public ListaCompletaResponseDTO adicionarQuestaoExistenteCompleto(UUID listaId, Integer questaoId) {
        Lista lista = listaRepository.findById(listaId)
                .orElseThrow(() -> new RuntimeException("Lista não encontrada"));

        // Usando método otimizado com FETCH JOIN
        Questao questao = questaoRepository.findByIdWithAlternativas(questaoId)
                .orElseThrow(() -> new RuntimeException("Questão não encontrada"));

        if (lista.getQuestoes() != null && lista.getQuestoes().contains(questao)) {
            throw new RuntimeException("Questão já está na lista");
        }

        questao.setLista(lista);
        if (lista.getQuestoes() == null) {
            lista.setQuestoes(new ArrayList<>());
        }
        lista.getQuestoes().add(questao);

        questaoRepository.save(questao);
        listaRepository.save(lista);

        // Buscar todas as questões com alternativas otimizadas
        List<Questao> questoesCompletas = questaoRepository.findByListaIdWithAlternativas(listaId);
        List<QuestaoResponseDTO> questaoDTOs = questoesCompletas.stream()
                .map(q -> new QuestaoResponseDTO(
                        q.getId(),
                        q.getCabecalho(),
                        q.getEnunciado(),
                        q.getAlternativasTexto(),
                        q.getGabarito()))
                .collect(toList());

        return new ListaCompletaResponseDTO(
                lista.getId(),
                lista.getTitulo(),
                lista.getProfessor().getNome(),
                questaoDTOs
        );
    }

    @CacheEvict(value = {"listaQuestoesCompact"}, allEntries = true)
    public ListaResponseDTO adicionarQuestoesEmLote(UUID listaId, List<Integer> questaoIds) {
        Lista lista = listaRepository.findById(listaId)
                .orElseThrow(() -> new RuntimeException("Lista não encontrada"));

        // Usando método otimizado com FETCH JOIN para múltiplas questões
        List<Questao> questões = questaoRepository.findAllByIdWithAlternativas(questaoIds);

        if (questões.size() != questaoIds.size()) {
            throw new RuntimeException("Uma ou mais questões não foram encontradas");
        }

        if (lista.getQuestoes() == null) {
            lista.setQuestoes(new ArrayList<>());
        }
        for (Questao questao : questões) {
            if (!lista.getQuestoes().contains(questao)) {
                // Ajusta relação bidirecional
                questao.setLista(lista);
                lista.getQuestoes().add(questao);
                questaoRepository.save(questao);
            }
        }

        listaRepository.save(lista);

        return new ListaResponseDTO(lista.getId(), lista.getTitulo(), lista.getProfessor().getNome());
    }

    @Transactional(readOnly = true)
    public List<QuestaoResponseDTO> buscarQuestoesPorLista(UUID listaId) {
        // Buscar questões da lista
        List<Questao> questoes = questaoRepository.findByListaIdWithAlternativas(listaId);

        // Inicializar alternativas dentro da transação
        questoes.forEach(q -> {
            if (q.getAlternativas() != null) {
                q.getAlternativas().size();
            }
        });

        return questoes.stream()
                .map(questao -> new QuestaoResponseDTO(
                        questao.getId(),
                        questao.getCabecalho(),
                        questao.getEnunciado(),
                        questao.getAlternativasTexto(),
                        questao.getGabarito()
                ))
                .collect(toList());
    }

    @CacheEvict(value = {"listaQuestoesCompact"}, allEntries = true)
    public ListaResponseDTO removerQuestao(UUID listaId, Integer questaoId) {
        Lista lista = listaRepository.findById(listaId)
                .orElseThrow(() -> new RuntimeException("Lista não encontrada"));

        // Usando método otimizado com FETCH JOIN
        Questao questao = questaoRepository.findByIdWithAlternativas(questaoId)
                .orElseThrow(() -> new RuntimeException("Questão não encontrada"));

        // Ajusta relação bidirecional: remove da coleção e limpa o lado dono
        if (lista.getQuestoes() != null) {
            lista.getQuestoes().remove(questao);
        }
        questao.setLista(null);

        questaoRepository.save(questao);
        listaRepository.save(lista);

        return new ListaResponseDTO(lista.getId(), lista.getTitulo(), lista.getProfessor().getNome());
    }

    // Métodos para Listas
    public ListaResponseDTO criarLista(String titulo, UUID professorId) {
        Professor professor = professorRepository.findById(professorId)
                .orElseThrow(() -> new RuntimeException("Professor não encontrado"));

        Lista lista = new Lista();
        lista.setTitulo(titulo);
        lista.setProfessor(professor);

        return convertToDTO(listaRepository.save(lista));
    }

    public ListaResponseDTO editarLista(UUID listaId, String novoTitulo) {
        Lista lista = listaRepository.findById(listaId)
                .orElseThrow(() -> new RuntimeException("Lista não encontrada"));

        lista.setTitulo(novoTitulo);
        return convertToDTO(listaRepository.save(lista));
    }

    public void excluirLista(UUID listaId) {
        if (!listaRepository.existsById(listaId)) {
            throw new RuntimeException("Lista não encontrada");
        }
        listaRepository.deleteById(listaId);
    }

    public List<ListaResponseDTO> buscarTodasListas() {
        List<Lista> listas = listaRepository.findAll();
        return listas.stream()
                .map(this::convertToDTO)
                .collect(toList());
    }

    public List<ListaResponseDTO> buscarListasPorProfessor(UUID professorId) {
        List<Lista> listas = listaRepository.findByProfessorId(professorId);
        return listas.stream()
                .map(this::convertToDTO)
                .collect(toList());
    }

    @Transactional(readOnly = true)
    public List<ListaResponseDTO> buscarListasPorInstituicao(UUID instituicaoId) {
        List<Lista> listas = listaRepository.findByDisciplina_Instituicao_Id(instituicaoId);
        return listas.stream()
                .map(this::convertToDTO)
                .collect(toList());
    }

    // Métodos para Estudantes
    public ListaResponseDTO adicionarEstudante(UUID listaId, UUID estudanteId) {
        Lista lista = listaRepository.findById(listaId)
                .orElseThrow(() -> new RuntimeException("Lista não encontrada"));

        Estudante estudante = estudanteRepositores.findById(estudanteId)
                .orElseThrow(() -> new RuntimeException("Estudante não encontrado"));

        // Verifica se o estudante já está na lista
        if (lista.getEstudantes().contains(estudante)) {
            throw new RuntimeException("Estudante já está na lista");
        }

        lista.getEstudantes().add(estudante);
        listaRepository.save(lista);

        return new ListaResponseDTO(lista.getId(), lista.getTitulo(), lista.getProfessor().getNome());
    }

    public ListaResponseDTO removerEstudante(UUID listaId, UUID estudanteId) {
        Lista lista = listaRepository.findById(listaId)
                .orElseThrow(() -> new RuntimeException("Lista não encontrada"));

        Estudante estudante = estudanteRepositores.findById(estudanteId)
                .orElseThrow(() -> new RuntimeException("Estudante não encontrado"));

        // Remove o estudante da lista
        lista.getEstudantes().remove(estudante);
        listaRepository.save(lista);

        return new ListaResponseDTO(lista.getId(), lista.getTitulo(), lista.getProfessor().getNome());
    }

    @Transactional(readOnly = true)
    public ListaCompletaResponseDTO buscarListaCompleta(UUID listaId) {
        Lista lista = listaRepository.findById(listaId)
                .orElseThrow(() -> new RuntimeException("Lista não encontrada"));

        // Buscar questões e inicializar alternativas dentro da transação
        List<Questao> questoes = questaoRepository.findByListaIdWithAlternativas(listaId);

        // Inicializar alternativas dentro da transação
        questoes.forEach(q -> {
            if (q.getAlternativas() != null) {
                q.getAlternativas().size();
            }
        });

        List<QuestaoResponseDTO> questaoDTOs = questoes.stream()
                .map(q -> new QuestaoResponseDTO(
                        q.getId(),
                        q.getCabecalho(),
                        q.getEnunciado(),
                        q.getAlternativasTexto(),
                        q.getGabarito()))
                .collect(toList());

        return new ListaCompletaResponseDTO(
                lista.getId(),
                lista.getTitulo(),
                lista.getProfessor().getNome(),
                questaoDTOs
        );
    }

    /**
     * Busca lista completa COM IMAGENS das questões (para visão do aluno)
     */
    @Transactional(readOnly = true)
    public Map<String, Object> buscarListaCompletaComImagens(UUID listaId) {
        Lista lista = listaRepository.findById(listaId)
                .orElseThrow(() -> new RuntimeException("Lista não encontrada"));

        // Usar serviço otimizado que já carrega imagens
        List<com.example.BancoDeDados.dto.QuestaoComAlternativasDTO> questoesComImagens =
                questaoOtimizadoService.buscarQuestoesComAlternativasPorLista(listaId);

        Map<String, Object> resultado = new HashMap<>();
        resultado.put("id", lista.getId());
        resultado.put("titulo", lista.getTitulo());
        resultado.put("professorNome", lista.getProfessor().getNome());
        resultado.put("questoes", questoesComImagens);
        resultado.put("totalQuestoes", questoesComImagens.size());

        return resultado;
    }

    @Transactional(readOnly = true)
    public List<ListaTopDTO> buscarTop10ListasPorDisciplinaComEstatisticas(UUID disciplinaId) {
        List<PiorListaProjection> piorListas = listaRepository.findTop10PioresListasByDisciplina(disciplinaId);
        List<UUID> listaIds = piorListas.stream().map(PiorListaProjection::getListaId).toList();
        // Query única para estatísticas de todas as questões dessas listas
        List<QuestaoStatsProjection> stats = questaoRepository.aggregateStatsByListaIds(listaIds);
        // Agrupar por listaId
        Map<UUID, List<QuestaoStatsDTO>> porLista = new HashMap<>();
        for (QuestaoStatsProjection s : stats) {
            int total = s.getTotalRespondidas() == null ? 0 : s.getTotalRespondidas().intValue();
            int acertos = s.getAcertos() == null ? 0 : s.getAcertos().intValue();
            int erros = total - acertos;
            porLista.computeIfAbsent(s.getListaId(), k -> new ArrayList<>())
                    .add(new QuestaoStatsDTO(s.getQuestaoId(), s.getEnunciado(), s.getGabarito(), total, acertos, erros));
        }
        List<ListaTopDTO> resultado = new ArrayList<>();
        for (PiorListaProjection proj : piorListas) {
            List<QuestaoStatsDTO> qs = porLista.getOrDefault(proj.getListaId(), new ArrayList<>());
            resultado.add(new ListaTopDTO(proj.getListaId(), proj.getTitulo(), qs));
        }
        return resultado;
    }

    // Método auxiliar
    private ListaResponseDTO convertToDTO(Lista lista) {
        return new ListaResponseDTO(
                lista.getId(),
                lista.getTitulo(),
                lista.getProfessor().getNome()
        );
    }
}
