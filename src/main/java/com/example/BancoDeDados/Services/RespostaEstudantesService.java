package com.example.BancoDeDados.Services;

import com.example.BancoDeDados.Exceptions.ResourceNotFoundException;
import com.example.BancoDeDados.Model.*;
import com.example.BancoDeDados.Repositores.*;
import com.example.BancoDeDados.ResponseDTO.*;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
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

        if (enviarRespostaDTO.alternativa() < 0 ||
                enviarRespostaDTO.alternativa() >= questao.getAlternativas().size()) {
            throw new IllegalArgumentException("Alternativa inválida para esta questão.");
        }

        Optional<RespostaEstudantes> respostaExistente =
                respostaEstudantesRepository.findByQuestaoIdAndEstudanteId(
                        enviarRespostaDTO.questaoId(),
                        enviarRespostaDTO.estudanteId()
                );

        RespostaEstudantes resposta;

        if (respostaExistente.isPresent()) {
            resposta = respostaExistente.get();
            resposta.setAlternativa(enviarRespostaDTO.alternativa());
        } else {
            resposta = new RespostaEstudantes();
            resposta.setQuestao(questao);
            resposta.setEstudante(estudante);
            resposta.setAlternativa(enviarRespostaDTO.alternativa());
        }

        resposta.setResposta(resposta.isCorreta());

        respostaEstudantesRepository.save(resposta);

        respostaEstudantesRepository.save(resposta);


        respostaEstudantesRepository.save(resposta);

        // Agora temos o listaId diretamente do DTO
        notaListaService.atualizarNotaAposResposta(enviarRespostaDTO.listaId(), estudante.getId());
        sincronizarComEventosAssociados(enviarRespostaDTO.listaId(), estudante.getId());;
    }
    private void sincronizarComEventosAssociados(UUID listaId, UUID estudanteId) {
        try {
            // Busca todos os eventos associados a esta lista
            List<ListaEvento> listaEventos = listaEventoRepository.findByListaId(listaId);

            for (ListaEvento listaEvento : listaEventos) {
                Evento evento = listaEvento.getEvento();

                // Sincroniza a nota deste estudante específico
                sincronizarNotaEstudanteParaEvento(listaId, estudanteId, evento);
            }
        } catch (Exception e) {
            System.err.println("Erro ao sincronizar com eventos: " + e.getMessage());
        }
    }
    private void sincronizarNotaEstudanteParaEvento(UUID listaId, UUID estudanteId, Evento evento) {
        try {
            Optional<ListaEstudanteNota> notaListaOpt = notaListaService.buscarNotaEstudanteOptional(listaId, estudanteId);

            if (notaListaOpt.isPresent()) {
                ListaEstudanteNota notaLista = notaListaOpt.get();

                NotaEvento notaEvento = notaEventoRepository.findByEstudanteIdAndEventoId(estudanteId, evento.getId())
                        .orElseGet(() -> {
                            NotaEvento novaNotaEvento = new NotaEvento();
                            novaNotaEvento.setEstudante(estudanteRepository.getReferenceById(estudanteId));
                            novaNotaEvento.setEvento(evento);
                            novaNotaEvento.setProfessor(evento.getProfessor());
                            novaNotaEvento.setStatusEntrega(NotaEvento.StatusEntrega.ENTREGUE);
                            return novaNotaEvento;
                        });

                Double notaConvertida = converterNotaListaParaEvento(notaLista.getNota(), evento.getNotaMaxima());
                notaEvento.setNota(notaConvertida);
                notaEvento.setObservacao("Nota atualizada automaticamente da lista");

                notaEventoRepository.save(notaEvento);
            }
        } catch (Exception e) {
            System.err.println("Erro ao sincronizar nota do estudante " + estudanteId + " para evento: " + e.getMessage());
        }
    }
    private Double converterNotaListaParaEvento(BigDecimal notaLista, Double notaMaximaEvento) {
        if (notaLista == null) {
            return null;
        }

        // Converte BigDecimal para double
        double notaListaDouble = notaLista.doubleValue();

        // Se a nota máxima do evento é 10, retorna direto
        if (notaMaximaEvento == 10.0) {
            return notaListaDouble;
        }

        // Faz a proporção: (notaLista / 10) * notaMaximaEvento
        return (notaListaDouble / 10.0) * notaMaximaEvento;
    }

    // Versão alternativa se estiver usando Double na ListaEstudanteNota:
    private Double converterNotaListaParaEvento(Double notaLista, Double notaMaximaEvento) {
        if (notaLista == null) {
            return null;
        }

        // Se a nota máxima do evento é 10, retorna direto
        if (notaMaximaEvento == 10.0) {
            return notaLista;
        }

        // Faz a proporção: (notaLista / 10) * notaMaximaEvento
        return (notaLista / 10.0) * notaMaximaEvento;
    }

    public List<Integer> buscarQuestoesPorListaEEstudante(UUID listaId, UUID estudanteId) {
        if (!estudanteRepository.existsById(estudanteId)) {
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
                resposta.isCorreta() // Campo calculado no backend
        );
    }

    /**
     * Busca desempenho por lista
     */
    public List<DesempenhoEstudanteDTO> buscarDesempenhoPorLista(UUID listaId) {
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
                        resposta.isCorreta() // Campo calculado no backend
                ))
                .collect(Collectors.toList());

        System.out.println("📊 Desempenho calculado: " + desempenho);

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

        // Busca todas as questões da lista de uma vez
        List<Questao> questõesDaLista = questaoRepository.findByLista_Id(listaId);
        Map<Integer, Questao> questaoMap = questõesDaLista.stream()
                .collect(Collectors.toMap(Questao::getId, q -> q));

        // Busca respostas existentes de uma vez
        List<Integer> questaoIds = multiplasRespostasDTO.respostas().stream()
                .map(RespostaQuestaoDTO::questaoId)
                .collect(Collectors.toList());

        List<RespostaEstudantes> respostasExistentes = respostaEstudantesRepository
                .findByEstudanteIdAndQuestaoIdIn(estudanteId, questaoIds);

        Map<Integer, RespostaEstudantes> respostaExistenteMap = respostasExistentes.stream()
                .collect(Collectors.toMap(resposta -> resposta.getQuestao().getId(), resposta -> resposta));

        List<RespostaEstudantes> respostasParaSalvar = new ArrayList<>();

        for (RespostaQuestaoDTO respostaDTO : multiplasRespostasDTO.respostas()) {
            Questao questao = questaoMap.get(respostaDTO.questaoId());

            if (questao == null) {
                throw new IllegalArgumentException("Questão " + respostaDTO.questaoId() + " não pertence à lista.");
            }

            if (respostaDTO.alternativa() < 0 || respostaDTO.alternativa() >= questao.getAlternativas().size()) {
                throw new IllegalArgumentException("Alternativa inválida para a questão " + respostaDTO.questaoId());
            }

            RespostaEstudantes resposta = respostaExistenteMap.get(respostaDTO.questaoId());

            if (resposta == null) {
                resposta = new RespostaEstudantes();
                resposta.setQuestao(questao);
                resposta.setEstudante(estudanteRepository.getReferenceById(estudanteId));
            }

            resposta.setAlternativa(respostaDTO.alternativa());
            resposta.setResposta(resposta.isCorreta());

            respostasParaSalvar.add(resposta);
        }

        respostaEstudantesRepository.saveAll(respostasParaSalvar);
        notaListaService.atualizarNotaAposResposta(multiplasRespostasDTO.listaId(), multiplasRespostasDTO.estudanteId());

    }

}