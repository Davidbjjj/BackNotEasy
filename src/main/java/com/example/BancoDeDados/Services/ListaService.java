package com.example.BancoDeDados.Services;

import com.example.BancoDeDados.Model.*;
import com.example.BancoDeDados.Repositores.*;
import com.example.BancoDeDados.ResponseDTO.AlunoNotaDTO;
import com.example.BancoDeDados.ResponseDTO.DisciplinaProfessorResponseDTO;
import com.example.BancoDeDados.ResponseDTO.ListaCompletaResponseDTO;
import com.example.BancoDeDados.ResponseDTO.ListaResponseDTO;
import com.example.BancoDeDados.ResponseDTO.QuestaoResponseDTO;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
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

    public List<AlunoNotaDTO> getNotasByListaId(UUID listaId) {
        List<ListaEstudanteNota> notas = listaEstudanteNotaRepository.findByListaId(listaId);
        return notas.stream()
                .map(nota -> new AlunoNotaDTO(nota.getEstudante().getNome(), nota.getNota()))
                .collect(Collectors.toList());
    }

    // Adicione este método no ListaService.java

    // No ListaService.java - Método corrigido
    @Transactional
    public List<QuestaoResponseDTO> buscarQuestoesPorEstudante(UUID estudanteId) {
        // 1. Buscar todas as listas associadas ao estudante
        List<Lista> listasDoEstudante = listaRepository.findByEstudanteId(estudanteId);

        if (listasDoEstudante.isEmpty()) {
            throw new RuntimeException("Estudante não está associado a nenhuma lista");
        }

        // 2. Coletar todas as questões de todas as listas
        List<QuestaoResponseDTO> todasQuestoes = new ArrayList<>();

        for (Lista lista : listasDoEstudante) {
            // Carregar as questões da lista
            Lista listaComQuestoes = listaRepository.findByIdWithQuestoes(lista.getId())
                    .orElseThrow(() -> new RuntimeException("Lista não encontrada: " + lista.getId()));

            if (listaComQuestoes.getQuestoes() != null && !listaComQuestoes.getQuestoes().isEmpty()) {
                List<QuestaoResponseDTO> questoesDaLista = listaComQuestoes.getQuestoes().stream()
                        .map(questao -> new QuestaoResponseDTO(
                                questao.getId(),
                                questao.getCabecalho(),
                                questao.getEnunciado(),
                                questao.getAlternativas(),
                                questao.getGabarito()
                        ))
                        .collect(Collectors.toList());

                todasQuestoes.addAll(questoesDaLista);
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
    public ListaResponseDTO adicionarQuestao(UUID listaId, Integer questaoId) {
        Lista lista = listaRepository.findById(listaId)
                .orElseThrow(() -> new RuntimeException("Lista não encontrada"));

        Questao questao = questaoRepository.findById(questaoId)
                .orElseThrow(() -> new RuntimeException("Questão não encontrada"));

        // Verifica se a questão já está na lista
        if (lista.getQuestoes().contains(questao)) {
            throw new RuntimeException("Questão já está na lista");
        }

        lista.getQuestoes().add(questao);
        Lista updatedLista = listaRepository.save(lista);

        return convertToDTO(updatedLista);
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

    @Transactional
    public ListaResponseDTO adicionarQuestaoExistente(UUID listaId, Integer questaoId) {
        // Use o método com JOIN FETCH para carregar as questões de uma vez
        Lista lista = listaRepository.findByIdWithQuestoes(listaId)
                .orElseThrow(() -> new RuntimeException("Lista não encontrada"));

        Questao questao = questaoRepository.findById(questaoId)
                .orElseThrow(() -> new RuntimeException("Questão não encontrada"));

        // Verifica se a questão já está na lista
        if (lista.getQuestoes().contains(questao)) {
            throw new RuntimeException("Questão já está na lista");
        }

        // Adiciona a questão à lista
        lista.getQuestoes().add(questao);
        listaRepository.save(lista);

        return new ListaResponseDTO(lista.getId(), lista.getTitulo(), lista.getProfessor().getNome());
    }

    public ListaCompletaResponseDTO adicionarQuestaoExistenteCompleto(UUID listaId, Integer questaoId) {
        Lista lista = listaRepository.findById(listaId)
                .orElseThrow(() -> new RuntimeException("Lista não encontrada"));

        Questao questao = questaoRepository.findById(questaoId)
                .orElseThrow(() -> new RuntimeException("Questão não encontrada"));

        // Verifica se a questão já está na lista
        if (lista.getQuestoes().contains(questao)) {
            throw new RuntimeException("Questão já está na lista");
        }

        // Adiciona a questão à lista
        lista.getQuestoes().add(questao);
        listaRepository.save(lista);

        // Converte as questões para DTO
        List<QuestaoResponseDTO> questaoDTOs = lista.getQuestoes().stream()
                .map(q -> new QuestaoResponseDTO(
                        q.getId(),
                        q.getCabecalho(),
                        q.getEnunciado(),
                        q.getAlternativas(),
                        q.getGabarito()))
                .collect(toList());

        return new ListaCompletaResponseDTO(
                lista.getId(),
                lista.getTitulo(),
                lista.getProfessor().getNome(),
                questaoDTOs
        );
    }

    public ListaResponseDTO adicionarQuestoesEmLote(UUID listaId, List<Integer> questaoIds) {
        Lista lista = listaRepository.findById(listaId)
                .orElseThrow(() -> new RuntimeException("Lista não encontrada"));

        List<Questao> questões = questaoRepository.findAllById(questaoIds);

        // Verifica se todas as questões foram encontradas
        if (questões.size() != questaoIds.size()) {
            throw new RuntimeException("Uma ou mais questões não foram encontradas");
        }

        // Adiciona as questões à lista (evitando duplicatas)
        for (Questao questao : questões) {
            if (!lista.getQuestoes().contains(questao)) {
                lista.getQuestoes().add(questao);
            }
        }

        listaRepository.save(lista);

        return new ListaResponseDTO(lista.getId(), lista.getTitulo(), lista.getProfessor().getNome());
    }

    public List<QuestaoResponseDTO> buscarQuestoesPorLista(UUID listaId) {
        Lista lista = listaRepository.findById(listaId)
                .orElseThrow(() -> new RuntimeException("Lista não encontrada"));

        return lista.getQuestoes().stream()
                .map(questao -> new QuestaoResponseDTO(
                        questao.getId(),
                        questao.getCabecalho(),
                        questao.getEnunciado(),
                        questao.getAlternativas(),
                        questao.getGabarito()
                ))
                .collect(toList());
    }

    public ListaResponseDTO removerQuestao(UUID listaId, Integer questaoId) {
        Lista lista = listaRepository.findById(listaId)
                .orElseThrow(() -> new RuntimeException("Lista não encontrada"));

        Questao questao = questaoRepository.findById(questaoId)
                .orElseThrow(() -> new RuntimeException("Questão não encontrada"));

        // Remove a questão da lista
        lista.getQuestoes().remove(questao);
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

    public ListaCompletaResponseDTO buscarListaCompleta(UUID listaId) {
        Lista lista = listaRepository.findById(listaId)
                .orElseThrow(() -> new RuntimeException("Lista não encontrada"));

        // Converte as questões para DTO
        List<QuestaoResponseDTO> questaoDTOs = lista.getQuestoes().stream()
                .map(q -> new QuestaoResponseDTO(
                        q.getId(),
                        q.getCabecalho(),
                        q.getEnunciado(),
                        q.getAlternativas(),
                        q.getGabarito()))
                .collect(toList());

        return new ListaCompletaResponseDTO(
                lista.getId(),
                lista.getTitulo(),
                lista.getProfessor().getNome(),
                questaoDTOs
        );
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