package com.example.BancoDeDados.Services;

import com.example.BancoDeDados.Model.Estudante;
import com.example.BancoDeDados.Model.Lista;
import com.example.BancoDeDados.Model.Professor;
import com.example.BancoDeDados.Model.Questao;
import com.example.BancoDeDados.Repositores.EstudanteRepositores;
import com.example.BancoDeDados.Repositores.ListaRepository;
import com.example.BancoDeDados.Repositores.ProfessorRepositores;
import com.example.BancoDeDados.Repositores.QuestaoRepositores;
import com.example.BancoDeDados.ResponseDTO.ListaCompletaResponseDTO;
import com.example.BancoDeDados.ResponseDTO.ListaResponseDTO;
import com.example.BancoDeDados.ResponseDTO.QuestaoResponseDTO;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
    private EstudanteRepositores estudanteRepositores;

    @Autowired
    private QuestaoRepositores questaoRepository;

    @Autowired
    private ProfessorRepositores professorRepository;

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