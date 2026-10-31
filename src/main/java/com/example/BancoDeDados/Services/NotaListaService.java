package com.example.BancoDeDados.Services;

import com.example.BancoDeDados.Exceptions.ResourceNotFoundException;
import com.example.BancoDeDados.Model.*;
import com.example.BancoDeDados.Repositores.*;
import com.example.BancoDeDados.ResponseDTO.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class NotaListaService {

    @Autowired
    private ListaEstudanteNotaRepository listaEstudanteNotaRepository;

    @Autowired
    private RespostaEstudantesRepository respostaEstudantesRepository;

    @Autowired
    private ListaRepository listaRepository;

    @Autowired
    private EstudanteRepositores estudanteRepository;

    /**
     * Calcula e salva a nota do estudante em uma lista
     */
    public ListaEstudanteNota calcularESalvarNota(UUID listaId, UUID estudanteId) {
        Lista lista = listaRepository.findById(listaId)
                .orElseThrow(() -> new RuntimeException("Lista não encontrada"));

        Estudante estudante = estudanteRepository.findById(estudanteId)
                .orElseThrow(() -> new RuntimeException("Estudante não encontrado"));

        // Busca todas as respostas do estudante para esta lista
        List<RespostaEstudantes> respostas = respostaEstudantesRepository
                .findByEstudanteIdAndQuestaoListaId(estudanteId, listaId);

        int totalQuestoes = lista.getQuestoes().size();
        int questoesRespondidas = respostas.size();
        int questoesCorretas = (int) respostas.stream()
                .filter(RespostaEstudantes::isCorreta)
                .count();

        // Calcula porcentagem de acertos
        BigDecimal porcentagemAcertos = totalQuestoes > 0 ?
                BigDecimal.valueOf(questoesCorretas)
                        .divide(BigDecimal.valueOf(totalQuestoes), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)) :
                BigDecimal.ZERO;

        // Calcula a nota (92% = 9.2)
        BigDecimal nota = porcentagemAcertos.divide(BigDecimal.TEN, 2, RoundingMode.HALF_UP);

        // Busca ou cria o registro de nota
        ListaEstudanteNota listaEstudanteNota = listaEstudanteNotaRepository
                .findByListaIdAndEstudanteId(listaId, estudanteId)
                .orElse(new ListaEstudanteNota());

        listaEstudanteNota.setLista(lista);
        listaEstudanteNota.setEstudante(estudante);
        listaEstudanteNota.setNota(nota);
        listaEstudanteNota.setPorcentagemAcertos(porcentagemAcertos);
        listaEstudanteNota.setQuestoesRespondidas(questoesRespondidas);
        listaEstudanteNota.setQuestoesCorretas(questoesCorretas);
        listaEstudanteNota.setTotalQuestoes(totalQuestoes);
        // Não altera finalizada aqui; apenas garante que permanece false até aluno finalizar explicitamente
        if (listaEstudanteNota.isFinalizada() && questoesRespondidas < totalQuestoes) {
            // Se por algum motivo finalizada está true mas perdeu questões (inconsistência), volta para false
            listaEstudanteNota.setFinalizada(false);
        }
        return listaEstudanteNotaRepository.save(listaEstudanteNota);
    }
    public Optional<ListaEstudanteNota> buscarNotaEstudanteOptional(UUID listaId, UUID estudanteId) {
        try {
            ListaEstudanteNota nota = buscarNotaEstudante(listaId, estudanteId);
            return Optional.of(nota);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Calcula notas para todos os estudantes de uma lista
     */
    public void calcularNotasParaTodosEstudantes(UUID listaId) {
        Lista lista = listaRepository.findById(listaId)
                .orElseThrow(() -> new RuntimeException("Lista não encontrada"));

        lista.getEstudantes().forEach(estudante -> {
            calcularESalvarNota(listaId, estudante.getId());
        });
    }

    /**
     * Busca a nota de um estudante em uma lista
     */
    public ListaEstudanteNota buscarNotaEstudante(UUID listaId, UUID estudanteId) {
        return listaEstudanteNotaRepository.findByListaIdAndEstudanteId(listaId, estudanteId)
                .orElseGet(() -> {
                    return calcularESalvarNota(listaId, estudanteId);
                });
    }

    /**
     * Busca todas as notas de uma lista
     */
    public List<ListaEstudanteNota> buscarNotasPorLista(UUID listaId) {
        return listaEstudanteNotaRepository.findByListaId(listaId);
    }

    /**
     * Busca todas as notas de um estudante
     */
    public List<ListaEstudanteNota> buscarNotasPorEstudante(UUID estudanteId) {
        return listaEstudanteNotaRepository.findByEstudanteId(estudanteId);
    }

    /**
     * Atualiza nota automaticamente quando uma resposta é salva
     */
    public void atualizarNotaAposResposta(UUID listaId, UUID estudanteId) {
        calcularESalvarNota(listaId, estudanteId);
    }
    public RespostasListaComNotaDTO buscarRespostasPorListaComNota(UUID listaId) {
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
                    dto.setEnunciado(resposta.getQuestao().getEnunciado());
                    dto.setEstudanteId(resposta.getEstudante().getId());
                    dto.setNomeEstudante(resposta.getEstudante().getNome());
                    dto.setAlternativa(resposta.getAlternativa());
                    dto.setCorreta(resposta.isCorreta());
                    return dto;
                })
                .collect(Collectors.toList());

        // Calcula as estatísticas gerais da lista
        EstatisticasLista estatisticas = calcularEstatisticasLista(lista, respostas);

        return new RespostasListaComNotaDTO(
                lista.getId(),
                lista.getTitulo(),
                respostaDTOs,
                estatisticas.getNotaLista(),
                estatisticas.getPorcentagemAcertos(),
                estatisticas.getTotalQuestoes(),
                estatisticas.getQuestoesRespondidas(),
                estatisticas.getQuestoesCorretas()
        );
    }
    public RespostasListaComEstatisticasDTO buscarRespostasPorListaComEstatisticas(UUID listaId) {
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
                    dto.setEnunciado(resposta.getQuestao().getEnunciado());
                    dto.setEstudanteId(resposta.getEstudante().getId());
                    dto.setNomeEstudante(resposta.getEstudante().getNome());
                    dto.setAlternativa(resposta.getAlternativa());
                    dto.setCorreta(resposta.isCorreta());
                    return dto;
                })
                .collect(Collectors.toList());

        // Calcula as estatísticas gerais da lista
        EstatisticasGerais estatisticas = calcularEstatisticasGerais(lista, respostas);

        return new RespostasListaComEstatisticasDTO(
                lista.getId(),
                lista.getTitulo(),
                respostaDTOs,
                estatisticas.getNotaMediaGeral(),
                estatisticas.getPorcentagemAcertosGeral(),
                estatisticas.getTotalQuestoes(),
                estatisticas.getTotalRespostas(),
                estatisticas.getTotalAcertos(),
                estatisticas.getTotalEstudantes()
        );
    }

    private EstatisticasGerais calcularEstatisticasGerais(Lista lista, List<RespostaEstudantes> respostas) {
        int totalQuestoes = lista.getQuestoes().size();

        // Agrupa respostas por estudante
        Map<UUID, List<RespostaEstudantes>> respostasPorEstudante = respostas.stream()
                .collect(Collectors.groupingBy(resposta -> resposta.getEstudante().getId()));

        int totalEstudantes = respostasPorEstudante.size();
        int totalRespostas = respostas.size();
        int totalAcertos = (int) respostas.stream().filter(RespostaEstudantes::isCorreta).count();

        // Calcula porcentagem de acertos geral
        BigDecimal porcentagemAcertosGeral = totalRespostas > 0 ?
                BigDecimal.valueOf(totalAcertos)
                        .divide(BigDecimal.valueOf(totalRespostas), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)) :
                BigDecimal.ZERO;

        // Calcula nota média geral
        BigDecimal notaMediaGeral = BigDecimal.ZERO;
        if (totalEstudantes > 0) {
            BigDecimal somaNotas = BigDecimal.ZERO;
            int estudantesComNota = 0;

            for (UUID estudanteId : respostasPorEstudante.keySet()) {
                try {
                    ListaEstudanteNota notaEstudante = buscarNotaEstudante(lista.getId(), estudanteId);
                    if (notaEstudante.getNota() != null) {
                        somaNotas = somaNotas.add(notaEstudante.getNota());
                        estudantesComNota++;
                    }
                } catch (Exception e) {
                    // Estudante sem nota calculada ainda
                }
            }

            if (estudantesComNota > 0) {
                notaMediaGeral = somaNotas.divide(BigDecimal.valueOf(estudantesComNota), 2, RoundingMode.HALF_UP);
            }
        }

        return new EstatisticasGerais(notaMediaGeral, porcentagemAcertosGeral, totalQuestoes, totalRespostas, totalAcertos, totalEstudantes);
    }

    // Classe auxiliar para estatísticas gerais
    private static class EstatisticasGerais {
        private final BigDecimal notaMediaGeral;
        private final BigDecimal porcentagemAcertosGeral;
        private final Integer totalQuestoes;
        private final Integer totalRespostas;
        private final Integer totalAcertos;
        private final Integer totalEstudantes;

        public EstatisticasGerais(BigDecimal notaMediaGeral, BigDecimal porcentagemAcertosGeral,
                                  Integer totalQuestoes, Integer totalRespostas, Integer totalAcertos, Integer totalEstudantes) {
            this.notaMediaGeral = notaMediaGeral;
            this.porcentagemAcertosGeral = porcentagemAcertosGeral;
            this.totalQuestoes = totalQuestoes;
            this.totalRespostas = totalRespostas;
            this.totalAcertos = totalAcertos;
            this.totalEstudantes = totalEstudantes;
        }

        // Getters
        public BigDecimal getNotaMediaGeral() { return notaMediaGeral; }
        public BigDecimal getPorcentagemAcertosGeral() { return porcentagemAcertosGeral; }
        public Integer getTotalQuestoes() { return totalQuestoes; }
        public Integer getTotalRespostas() { return totalRespostas; }
        public Integer getTotalAcertos() { return totalAcertos; }
        public Integer getTotalEstudantes() { return totalEstudantes; }
    }
    private EstatisticasLista calcularEstatisticasLista(Lista lista, List<RespostaEstudantes> respostas) {
        int totalQuestoes = lista.getQuestoes().size();

        // Agrupa respostas por estudante
        Map<UUID, List<RespostaEstudantes>> respostasPorEstudante = respostas.stream()
                .collect(Collectors.groupingBy(resposta -> resposta.getEstudante().getId()));

        int totalEstudantes = respostasPorEstudante.size();
        int totalRespostas = respostas.size();

        // Calcula totais gerais
        int totalQuestoesCorretas = (int) respostas.stream()
                .filter(RespostaEstudantes::isCorreta)
                .count();

        // Calcula porcentagem de acertos geral
        BigDecimal porcentagemAcertos = totalRespostas > 0 ?
                BigDecimal.valueOf(totalQuestoesCorretas)
                        .divide(BigDecimal.valueOf(totalRespostas), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100)) :
                BigDecimal.ZERO;

        // Calcula nota geral (média das notas dos estudantes)
        BigDecimal notaGeral = BigDecimal.ZERO;
        if (totalEstudantes > 0) {
            BigDecimal somaNotas = BigDecimal.ZERO;
            int estudantesComNota = 0;

            for (UUID estudanteId : respostasPorEstudante.keySet()) {
                try {
                    ListaEstudanteNota notaEstudante = buscarNotaEstudante(lista.getId(), estudanteId);
                    if (notaEstudante.getNota() != null) {
                        somaNotas = somaNotas.add(notaEstudante.getNota());
                        estudantesComNota++;
                    }
                } catch (Exception e) {
                    // Estudante sem nota calculada ainda
                }
            }

            if (estudantesComNota > 0) {
                notaGeral = somaNotas.divide(BigDecimal.valueOf(estudantesComNota), 2, RoundingMode.HALF_UP);
            }
        }

        return new EstatisticasLista(notaGeral, porcentagemAcertos, totalQuestoes, totalRespostas, totalQuestoesCorretas);
    }

    // Classe auxiliar para estatísticas
    private static class EstatisticasLista {
        private final BigDecimal notaLista;
        private final BigDecimal porcentagemAcertos;
        private final Integer totalQuestoes;
        private final Integer questoesRespondidas;
        private final Integer questoesCorretas;

        public EstatisticasLista(BigDecimal notaLista, BigDecimal porcentagemAcertos,
                                 Integer totalQuestoes, Integer questoesRespondidas, Integer questoesCorretas) {
            this.notaLista = notaLista;
            this.porcentagemAcertos = porcentagemAcertos;
            this.totalQuestoes = totalQuestoes;
            this.questoesRespondidas = questoesRespondidas;
            this.questoesCorretas = questoesCorretas;
        }

        // Getters
        public BigDecimal getNotaLista() { return notaLista; }
        public BigDecimal getPorcentagemAcertos() { return porcentagemAcertos; }
        public Integer getTotalQuestoes() { return totalQuestoes; }
        public Integer getQuestoesRespondidas() { return questoesRespondidas; }
        public Integer getQuestoesCorretas() { return questoesCorretas; }
    }
    public RespostasListaComNotaDTO buscarRespostasPorListaComNotaPorEstudante(UUID listaId, UUID estudanteId) {
        // Primeiro, verifica se a lista existe
        Lista lista = listaRepository.findById(listaId)
                .orElseThrow(() -> new ResourceNotFoundException("Lista não encontrada com id: " + listaId));

        // Verifica se o estudante existe
        Estudante estudante = estudanteRepository.findById(estudanteId)
                .orElseThrow(() -> new ResourceNotFoundException("Estudante não encontrado"));

        // Busca as respostas do estudante específico
        List<RespostaEstudantes> respostas = respostaEstudantesRepository
                .findByEstudanteIdAndQuestaoListaId(estudanteId, listaId);

        // Converte para DTOs
        List<RespostaEstudanteQuestaoDTO> respostaDTOs = respostas.stream()
                .map(resposta -> {
                    RespostaEstudanteQuestaoDTO dto = new RespostaEstudanteQuestaoDTO();
                    dto.setRespostaId(resposta.getId());
                    dto.setQuestaoId(resposta.getQuestao().getId());
                    dto.setEnunciado(resposta.getQuestao().getEnunciado());
                    dto.setEstudanteId(resposta.getEstudante().getId());
                    dto.setNomeEstudante(resposta.getEstudante().getNome());
                    dto.setAlternativa(resposta.getAlternativa());
                    dto.setCorreta(resposta.isCorreta());
                    return dto;
                })
                .collect(Collectors.toList());


        ListaEstudanteNota notaEstudante = buscarNotaEstudante(listaId, estudanteId);

        return new RespostasListaComNotaDTO(
                lista.getId(),
                lista.getTitulo(),
                respostaDTOs,
                notaEstudante.getNota(),
                notaEstudante.getPorcentagemAcertos(),
                notaEstudante.getTotalQuestoes(),
                notaEstudante.getQuestoesRespondidas(),
                notaEstudante.getQuestoesCorretas()
        );
     }

    /**
     * Verifica se o estudante já respondeu pelo menos uma questão da lista
     */
    public boolean alunoRespondeuLista(UUID listaId, UUID estudanteId) {
        return !respostaEstudantesRepository.findByEstudanteIdAndQuestaoListaId(estudanteId, listaId).isEmpty();
    }

    /**
     * Finaliza a lista para o aluno se todas as questões foram respondidas.
     */
    public ListaEstudanteNota finalizarListaAluno(UUID listaId, UUID estudanteId) {
        Lista lista = listaRepository.findById(listaId)
                .orElseThrow(() -> new ResourceNotFoundException("Lista não encontrada"));
        ListaEstudanteNota nota = buscarNotaEstudante(listaId, estudanteId);
        if (nota.getQuestoesRespondidas() == null || nota.getTotalQuestoes() == null) {
            throw new RuntimeException("Dados de questões incompletos para finalizar");
        }
        if (nota.getQuestoesRespondidas() < nota.getTotalQuestoes()) {
            throw new RuntimeException("Ainda existem questões não respondidas. Responda todas para finalizar.");
        }
        nota.setFinalizada(true);
        return listaEstudanteNotaRepository.save(nota);
    }
}
