package com.example.BancoDeDados.Services;

import com.example.BancoDeDados.Exceptions.ResourceNotFoundException;
import com.example.BancoDeDados.Model.*;
import com.example.BancoDeDados.Repositores.*;
import com.example.BancoDeDados.ResponseDTO.*;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class RespostaEstudantesService {

    @Autowired
    private RespostaEstudantesRepository respostaEstudantesRepository;

    @Autowired
    private ListaRepository listaRepository;

    @Autowired
    private QuestaoRepositores questaoRepository;

    @Autowired
    private EstudanteRepositores estudanteRepository;

    @Autowired
    private NotaListaService notaListaService;

    @Autowired
    private NotaEventoRepository notaEventoRepository;

    @Autowired
    private ListaEventoRepository listaEventoRepository;


    public RespostaEstudantesService(
            RespostaEstudantesRepository respostaEstudantesRepository,
            QuestaoRepositores questaoRepository,
            EstudanteRepositores estudanteRepository,
            ListaRepository listaRepository,
            NotaListaService notaListaService) {
        this.respostaEstudantesRepository = respostaEstudantesRepository;
        this.questaoRepository = questaoRepository;
        this.estudanteRepository = estudanteRepository;
        this.listaRepository = listaRepository;
        this.notaListaService= notaListaService;
    }

    /**
     * Salva a resposta do estudante para uma determinada questão.
     */
    public void salvarResposta(EnviarRespostaDTO enviarRespostaDTO) {
        Questao questao = questaoRepository.findById(enviarRespostaDTO.questaoId())
                .orElseThrow(() -> new IllegalArgumentException("Questão não encontrada."));

        Estudante estudante = estudanteRepository.findById(enviarRespostaDTO.estudanteId())
                .orElseThrow(() -> new IllegalArgumentException("Estudante não encontrado."));

        notaListaService.validarRespostaPermitida(enviarRespostaDTO.listaId(), estudante.getId());

        if (questao.isAberta()) {
            validarTexto(enviarRespostaDTO.textoResposta());
        } else if (enviarRespostaDTO.alternativa() == null || enviarRespostaDTO.alternativa() < 0 ||
                enviarRespostaDTO.alternativa() >= questao.getAlternativas().size()) {
            throw new IllegalArgumentException("Alternativa inválida para esta questão.");
        }

        Optional<RespostaEstudantes> respostaExistente =
                respostaEstudantesRepository.findByQuestaoIdAndEstudanteId(
                        enviarRespostaDTO.questaoId(),
                        enviarRespostaDTO.estudanteId()
                );

        RespostaEstudantes resposta = respostaExistente.orElseGet(() -> {
            RespostaEstudantes r = new RespostaEstudantes();
            r.setQuestao(questao);
            r.setEstudante(estudante);
            return r;
        });
        aplicarResposta(resposta, questao, enviarRespostaDTO.alternativa(), enviarRespostaDTO.textoResposta());

        respostaEstudantesRepository.save(resposta);

        notaListaService.atualizarNotaAposResposta(enviarRespostaDTO.listaId(), estudante.getId());
    }

    private static void validarTexto(String texto) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("Escreva a resposta da questão aberta.");
        }
        if (texto.length() > 10000) {
            throw new IllegalArgumentException("A resposta aberta pode ter no máximo 10.000 caracteres.");
        }
    }

    /** Objetiva: corrige na hora. Aberta: guarda o texto e zera a correção anterior (volta à fila). */
    private static void aplicarResposta(RespostaEstudantes resposta, Questao questao, Integer alternativa, String texto) {
        if (questao.isAberta()) {
            boolean mudou = !texto.equals(resposta.getTextoResposta());
            resposta.setTextoResposta(texto);
            resposta.setAlternativa(null);
            if (mudou) {
                resposta.setResposta(null);
                resposta.setNotaQuestao(null);
                resposta.setFeedback(null);
                resposta.setCorrigidaEm(null);
            }
        } else {
            resposta.setAlternativa(alternativa);
            resposta.setTextoResposta(null);
            resposta.setResposta(resposta.isCorreta());
        }
    }

    /**
     * Busca desempenho por lista
     */
    public List<DesempenhoEstudanteDTO> buscarDesempenhoPorLista(UUID listaId) {
        // Buscar questões da lista
        List<Questao> questoesDaLista = questaoRepository.findByLista_Id(listaId);

        if (questoesDaLista.isEmpty()) {
            return Collections.emptyList();
        }

        List<Integer> questoesIds = questoesDaLista.stream()
                .map(Questao::getId)
                .collect(Collectors.toList());

        List<DesempenhoEstudanteDTO> desempenho = respostaEstudantesRepository.findAll().stream()
                .filter(resposta -> questoesIds.contains(resposta.getQuestao().getId()))
                .map(resposta -> new DesempenhoEstudanteDTO(
                        resposta.getEstudante().getId(),
                        resposta.getQuestao().getId(),
                        resposta.isCorreta() // Campo calculado no backend
                ))
                .collect(Collectors.toList());

        return desempenho;
    }

    /**
     * Busca questões respondidas por lista e estudante
     */
    public List<Integer> buscarQuestoesRespondidasPorListaEEstudante(UUID listaId, UUID estudanteId) {
        // Verificar se estudante existe
        if (!estudanteRepository.existsById(estudanteId)) {
            throw new ResourceNotFoundException("Estudante não encontrado com ID: " + estudanteId);
        }

        // Verificar se a lista existe
        if (!listaRepository.existsById(listaId)) {
            throw new ResourceNotFoundException("Lista não encontrada com ID: " + listaId);
        }

        // Buscar IDs das questões já respondidas pelo estudante na lista específica
        return respostaEstudantesRepository.findQuestoesRespondidasByEstudanteAndLista(estudanteId, listaId);
    }

    /**
     * Calcula pontuação por lista
     */
    public Double calcularPontuacaoPorLista(UUID listaId, UUID estudanteId) {
        List<RespostaEstudantes> respostas = respostaEstudantesRepository.findByEstudanteIdAndQuestaoListaId(estudanteId, listaId);

        if (respostas.isEmpty()) {
            return 0.0;
        }

        long respostasCorretas = respostas.stream()
                .filter(RespostaEstudantes::isCorreta)
                .count();

        return (double) respostasCorretas / respostas.size() * 100;
    }

    /**
     * Busca resposta por questão e estudante
     * Lança exceção se não encontrar
     */
    public RespostaEstudanteDTO buscarRespostaPorQuestaoEEstudante(Integer questaoId, UUID estudanteId) {
        RespostaEstudantes resposta = respostaEstudantesRepository
                .findByQuestaoIdAndEstudanteId(questaoId, estudanteId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Resposta não encontrada para a questão " + questaoId + " e estudante " + estudanteId
                ));

        return new RespostaEstudanteDTO(
                resposta.getId(),
                resposta.getQuestao().getId(),
                resposta.getEstudante().getId(),
                resposta.getAlternativa(),
                resposta.isCorreta()
        );
    }

    /**
     * Busca resposta por questão e estudante ou retorna vazio
     */
    public RespostaEstudanteDTO buscarRespostaPorQuestaoEEstudanteOuVazio(Integer questaoId, UUID estudanteId) {
        Optional<RespostaEstudantes> respostaOpt = respostaEstudantesRepository
                .findByQuestaoIdAndEstudanteId(questaoId, estudanteId);

        if (respostaOpt.isPresent()) {
            RespostaEstudantes resposta = respostaOpt.get();
            return new RespostaEstudanteDTO(
                    resposta.getId(),
                    resposta.getQuestao().getId(),
                    resposta.getEstudante().getId(),
                    resposta.getAlternativa(),
                    resposta.isCorreta() // Campo calculado no backend
            );
        } else {
            // Retorna um DTO vazio para indicar que não há resposta
            return new RespostaEstudanteDTO(null, questaoId, estudanteId, null, null);
        }
    }

    /**
     * Busca todas as respostas por lista
     */
    public RespostasListaDTO buscarRespostasPorLista(UUID listaId) {
        // Primeiro, verifica se a lista existe
        Lista lista = listaRepository.findById(listaId)
                .orElseThrow(() -> new ResourceNotFoundException("Lista não encontrada com id: " + listaId));

        // Busca todas as respostas para as questões desta lista
        List<RespostaEstudantes> respostas = respostaEstudantesRepository.findByListaIdWithJoins(listaId);

        // Converte para DTOs
        List<RespostaEstudanteQuestaoDTO> respostaDTOs = respostas.stream()
                .map(resposta -> {
                    RespostaEstudanteQuestaoDTO dto = new RespostaEstudanteQuestaoDTO();
                    dto.setRespostaId(resposta.getId());
                    dto.setQuestaoId(resposta.getQuestao().getId());
                    dto.setEstudanteId(resposta.getEstudante().getId());
                    dto.setNomeEstudante(resposta.getEstudante().getNome());
                    dto.setAlternativa(resposta.getAlternativa());
                    dto.setCorreta(resposta.isCorreta()); // Campo calculado no backend
                    return dto;
                })
                .collect(Collectors.toList());

        return new RespostasListaDTO(
                lista.getId(),
                lista.getTitulo(),
                respostaDTOs
        );
    }
    /**
     * Versão otimizada para salvar múltiplas respostas
     */
    @Transactional
    public void salvarMultiplasRespostasOtimizado(EnviarMultiplasRespostasDTO multiplasRespostasDTO) {
        UUID estudanteId = multiplasRespostasDTO.estudanteId();
        UUID listaId = multiplasRespostasDTO.listaId();

        // Validações básicas
        if (!estudanteRepository.existsById(estudanteId)) {
            throw new IllegalArgumentException("Estudante não encontrado.");
        }

        if (!listaRepository.existsById(listaId)) {
            throw new IllegalArgumentException("Lista não encontrada.");
        }

        notaListaService.validarRespostaPermitida(listaId, estudanteId);

        // Busca todas as questões da lista de uma vez
        List<Questao> questõesDaLista = questaoRepository.findByLista_Id(listaId);

        // Inicializar alternativas dentro da transação
        questõesDaLista.forEach(q -> {
            if (q.getAlternativas() != null) {
                q.getAlternativas().size();
            }
        });

        Map<Integer, Questao> questaoMap = questõesDaLista.stream()
                .collect(Collectors.toMap(Questao::getId, q -> q));

        // Busca respostas existentes de uma vez
        List<Integer> questaoIds = multiplasRespostasDTO.respostas().stream()
                .map(RespostaQuestaoDTO::questaoId)
                .collect(Collectors.toList());

        List<RespostaEstudantes> respostasExistentes = respostaEstudantesRepository.findByEstudanteIdAndQuestaoIdIn(estudanteId, questaoIds);

        Map<Integer, RespostaEstudantes> respostaExistenteMap = respostasExistentes.stream()
                .collect(Collectors.toMap(resposta -> resposta.getQuestao().getId(), resposta -> resposta));

        List<RespostaEstudantes> respostasParaSalvar = new ArrayList<>();

        for (RespostaQuestaoDTO respostaDTO : multiplasRespostasDTO.respostas()) {
            Questao questao = questaoMap.get(respostaDTO.questaoId());

            if (questao == null) {
                throw new IllegalArgumentException("Questão " + respostaDTO.questaoId() + " não pertence à lista.");
            }

            if (questao.isAberta()) {
                validarTexto(respostaDTO.textoResposta());
            } else if (respostaDTO.alternativa() == null || respostaDTO.alternativa() < 0
                    || respostaDTO.alternativa() >= questao.getAlternativas().size()) {
                throw new IllegalArgumentException("Alternativa inválida para a questão " + respostaDTO.questaoId());
            }

            RespostaEstudantes resposta = respostaExistenteMap.get(respostaDTO.questaoId());

            if (resposta == null) {
                resposta = new RespostaEstudantes();
                resposta.setQuestao(questao);
                resposta.setEstudante(estudanteRepository.getReferenceById(estudanteId));
            }

            aplicarResposta(resposta, questao, respostaDTO.alternativa(), respostaDTO.textoResposta());

            respostasParaSalvar.add(resposta);
        }

        respostaEstudantesRepository.saveAll(respostasParaSalvar);
        notaListaService.atualizarNotaAposResposta(multiplasRespostasDTO.listaId(), multiplasRespostasDTO.estudanteId());

    }

}

