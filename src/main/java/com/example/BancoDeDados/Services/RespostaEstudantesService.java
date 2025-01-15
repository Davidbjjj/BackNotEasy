package com.example.BancoDeDados.Services;

import com.example.BancoDeDados.Model.Estudante;
import com.example.BancoDeDados.Model.Questao;
import com.example.BancoDeDados.Repositores.EstudanteRepositores;
import com.example.BancoDeDados.Repositores.ListaRepository;
import com.example.BancoDeDados.Repositores.QuestaoRepositores;
import com.example.BancoDeDados.ResponseDTO.DesempenhoEstudanteDTO;
import com.example.BancoDeDados.ResponseDTO.EnviarRespostaDTO;
import com.example.BancoDeDados.ResponseDTO.RespostaEstudanteDTO;
import com.example.BancoDeDados.Model.RespostaEstudantes;
import com.example.BancoDeDados.Repositores.RespostaEstudantesRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class RespostaEstudantesService {

    @Autowired
    private RespostaEstudantesRepository respostaEstudantesRepository;

    @Autowired
    private ListaRepository listaRepository;

    @Autowired
    private QuestaoRepositores questaoRepository;

    @Autowired
    private EstudanteRepositores estudanteRepository;

    public RespostaEstudantesService(
            RespostaEstudantesRepository respostaEstudantesRepository,
            QuestaoRepositores questaoRepository,
            EstudanteRepositores estudanteRepository,
            ListaRepository listaRepository) {
        this.respostaEstudantesRepository = respostaEstudantesRepository;
        this.questaoRepository = questaoRepository;
        this.estudanteRepository = estudanteRepository;
        this.listaRepository = listaRepository;
    }

    /**
     * Salva a resposta do estudante para uma determinada questão.
     */
    public void salvarResposta(EnviarRespostaDTO enviarRespostaDTO) {
        Questao questao = questaoRepository.findById(enviarRespostaDTO.getQuestaoId())
                .orElseThrow(() -> new IllegalArgumentException("Questão não encontrada."));

        Estudante estudante = estudanteRepository.findById(enviarRespostaDTO.getEstudanteId())
                .orElseThrow(() -> new IllegalArgumentException("Estudante não encontrado."));

        RespostaEstudantes resposta = new RespostaEstudantes();
        resposta.setQuestao(questao);
        resposta.setEstudante(estudante);
        resposta.setResposta(enviarRespostaDTO.getResposta());

        respostaEstudantesRepository.save(resposta);
    }

    /**
     * Retorna as questões associadas a uma lista específica para um determinado estudante.
     */
    public List<Integer> buscarQuestoesPorListaEEstudante(Long listaId, Long estudanteId) {
        if (!estudanteRepository.existsById(Math.toIntExact(estudanteId))) {
            throw new IllegalArgumentException("Estudante não encontrado.");
        }

        return listaRepository.findById(listaId)
                .map(lista -> lista.getQuestoes().stream()
                        .map(Questao::getId)
                        .collect(Collectors.toList()))
                .orElseThrow(() -> new IllegalArgumentException("Lista não encontrada."));
    }

    /**
     * Busca a resposta de um estudante para uma questão específica.
     */
    public RespostaEstudanteDTO buscarRespostaPorQuestaoEEstudante(Long questaoId, Long estudanteId) {
        RespostaEstudantes resposta = respostaEstudantesRepository
                .findByQuestaoIdAndEstudanteId(questaoId, estudanteId)
                .orElseThrow(() -> new IllegalArgumentException("Resposta não encontrada."));

        return new RespostaEstudanteDTO(
                resposta.getId(),
                resposta.getQuestao().getId(),
                resposta.getEstudante().getId(),
                resposta.getResposta()
        );
    }
    public List<DesempenhoEstudanteDTO> buscarDesempenhoPorLista(Long listaId) {
        List<Questao> questoesDaLista = questaoRepository.findByLista_Id(listaId);

        System.out.println("🔍 Questões encontradas para a lista " + listaId + ": " + questoesDaLista);

        if (questoesDaLista.isEmpty()) {
            System.out.println("⚠️ Nenhuma questão encontrada para a lista.");
            return Collections.emptyList();
        }

        List<Integer> questoesIds = questoesDaLista.stream()
                .map(Questao::getId)
                .collect(Collectors.toList());

        System.out.println("🆔 IDs das questões na lista: " + questoesIds);

        List<DesempenhoEstudanteDTO> desempenho = respostaEstudantesRepository.findAll().stream()
                .filter(resposta -> questoesIds.contains(resposta.getQuestao().getId()))
                .map(resposta -> new DesempenhoEstudanteDTO(
                        resposta.getEstudante().getId(),
                        resposta.getQuestao().getId(),
                        resposta.isCorreta()
                ))
                .collect(Collectors.toList());

        System.out.println("📊 Desempenho calculado: " + desempenho);

        return desempenho;
    }



}
