package com.example.BancoDeDados.Services;

import com.example.BancoDeDados.Model.Questao;
import com.example.BancoDeDados.Model.QuestaoAlternativa;
import com.example.BancoDeDados.Model.QuestaoImagem;
import com.example.BancoDeDados.Repositores.QuestaoAlternativaRepository;
import com.example.BancoDeDados.Repositores.QuestaoRepositores;
import com.example.BancoDeDados.dto.QuestaoComAlternativasDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QuestaoOtimizadoService {

    private final QuestaoRepositores questaoRepositores;
    private final QuestaoAlternativaRepository alternativaRepository;

    /**
     * ✅ Busca questões com alternativas em 2 queries otimizadas
     * Evita N+1 problem completamente
     */
    @Transactional(readOnly = true)
    public List<QuestaoComAlternativasDTO> buscarQuestoesComAlternativasPorLista(UUID listaId) {
        return buscarQuestoesComAlternativasPorLista(listaId, true);
    }

    /**
     * @param incluirGabarito false para visões de aluno: gabarito/assunto/explicação saem null
     */
    @Transactional(readOnly = true)
    public List<QuestaoComAlternativasDTO> buscarQuestoesComAlternativasPorLista(UUID listaId, boolean incluirGabarito) {
        // 1️⃣ Buscar questões (1 query)
        List<Questao> questoes = questaoRepositores.findByListaId(listaId);

        if (questoes.isEmpty()) {
            return Collections.emptyList();
        }

        // 2️⃣ Buscar TODAS as alternativas em 1 query
        List<Integer> questaoIds = questoes.stream()
            .map(Questao::getId)
            .collect(Collectors.toList());

        List<QuestaoAlternativa> todasAlternativas =
            alternativaRepository.findByQuestaoIdInOrderByOrdem(questaoIds);

        // 3️⃣ Agrupar alternativas por questao_id (em memória - muito rápido)
        Map<Integer, List<QuestaoAlternativa>> alternativasPorQuestao =
            todasAlternativas.stream()
                .collect(Collectors.groupingBy(a -> a.getQuestao().getId()));

        // 4️⃣ Montar DTOs
        return questoes.stream()
            .map(q -> QuestaoComAlternativasDTO.builder()
                .id(q.getId())
                .cabecalho(q.getCabecalho())
                .enunciado(q.getEnunciado())
                .gabarito(incluirGabarito ? q.getGabarito() : null)
                .assunto(incluirGabarito ? q.getAssunto() : null)
                .explicacao(incluirGabarito ? q.getExplicacao() : null)
                .tipo(q.isAberta() ? "ABERTA" : "OBJETIVA")
                .rubrica(incluirGabarito ? q.getRubrica() : null)
                .dificuldade(incluirGabarito ? q.getDificuldade() : null)
                .tags(incluirGabarito ? new java.util.ArrayList<>(q.getTags()) : null)
                .alternativas(montarAlternativas(alternativasPorQuestao.getOrDefault(q.getId(), Collections.emptyList()),
                        converterImagensParaDTO(q.getImagens())))
                .imagens(imagensDoEnunciado(converterImagensParaDTO(q.getImagens())))
                .build())
            .collect(Collectors.toList());
    }

    /**
     * FE-205: embaralha questões e/ou alternativas para o aluno. A ordem é estável por aluno+lista
     * (mesma semente), e cada alternativa mantém a sua {@code ordem} original, que é o que o gabarito usa.
     */
    public List<QuestaoComAlternativasDTO> embaralharParaAluno(UUID listaId, UUID estudanteId,
                                                              List<QuestaoComAlternativasDTO> questoes,
                                                              boolean questoesFlag, boolean alternativasFlag) {
        if (estudanteId == null || (!questoesFlag && !alternativasFlag)) return questoes;
        long seed = listaId.hashCode() * 31L + estudanteId.hashCode();
        List<QuestaoComAlternativasDTO> saida = new ArrayList<>(questoes);
        if (questoesFlag) Collections.shuffle(saida, new Random(seed));
        if (alternativasFlag) {
            for (QuestaoComAlternativasDTO q : saida) {
                if (q.getAlternativas() == null || q.getAlternativas().size() < 2) continue;
                List<QuestaoComAlternativasDTO.AlternativaDTO> alts = new ArrayList<>(q.getAlternativas());
                Collections.shuffle(alts, new Random(seed + q.getId()));
                q.setAlternativas(alts);
            }
        }
        return saida;
    }

    /**
     * ✅ Busca uma questão com alternativas
     */
    @Transactional(readOnly = true)
    public Optional<QuestaoComAlternativasDTO> buscarQuestaoComAlternativasPorId(Integer id) {
        Optional<Questao> questaoOpt = questaoRepositores.findById(id);

        if (questaoOpt.isEmpty()) {
            return Optional.empty();
        }

        Questao q = questaoOpt.get();
        List<QuestaoAlternativa> alternativas =
            alternativaRepository.findByQuestaoIdOrderByOrdem(id);

        return Optional.of(QuestaoComAlternativasDTO.builder()
            .id(q.getId())
            .cabecalho(q.getCabecalho())
            .enunciado(q.getEnunciado())
            .gabarito(q.getGabarito())
            .alternativas(montarAlternativas(alternativas, converterImagensParaDTO(q.getImagens())))
            .imagens(imagensDoEnunciado(converterImagensParaDTO(q.getImagens())))
            .build());
    }

    /**
     * ✅ Criar questão com alternativas
     */
    @Transactional
    public Questao criarQuestaoComAlternativas(
            String cabecalho,
            String enunciado,
            List<String> alternativasTexto,
            Integer gabarito) {

        if (alternativasTexto == null || alternativasTexto.isEmpty()) {
            throw new IllegalArgumentException("A lista de alternativas não pode ser vazia.");
        }

        if (gabarito == null || gabarito < 0 || gabarito >= alternativasTexto.size()) {
            throw new IllegalArgumentException("Índice do gabarito inválido.");
        }

        Questao questao = Questao.builder()
            .cabecalho(cabecalho)
            .enunciado(enunciado)
            .gabarito(gabarito)
            .build();

        // Adicionar alternativas
        for (int i = 0; i < alternativasTexto.size(); i++) {
            questao.addAlternativa(alternativasTexto.get(i), i);
        }

        return questaoRepositores.save(questao);
    }

    /**
     * Alternativas da questão; a que é uma figura (ex.: um dos cinco gráficos) leva a própria imagem,
     * casada pela ordem (0 = A) com {@code QuestaoImagem.alternativaIndice}.
     */
    private List<QuestaoComAlternativasDTO.AlternativaDTO> montarAlternativas(List<QuestaoAlternativa> alternativas,
                                                                             List<QuestaoComAlternativasDTO.ImagemDTO> imagens) {
        Map<Integer, QuestaoComAlternativasDTO.ImagemDTO> porAlternativa = new HashMap<>();
        for (QuestaoComAlternativasDTO.ImagemDTO img : imagens) {
            if (img.getAlternativaIndice() != null) porAlternativa.putIfAbsent(img.getAlternativaIndice(), img);
        }
        return alternativas.stream()
            .map(a -> QuestaoComAlternativasDTO.AlternativaDTO.builder()
                .id(a.getId())
                .ordem(a.getOrdem())
                .texto(a.getTexto())
                .imagem(a.getOrdem() == null ? null : porAlternativa.get(a.getOrdem()))
                .build())
            .collect(Collectors.toList());
    }

    /** Imagens que aparecem com o enunciado: as que são alternativas vão dentro da alternativa, não aqui. */
    private List<QuestaoComAlternativasDTO.ImagemDTO> imagensDoEnunciado(List<QuestaoComAlternativasDTO.ImagemDTO> imagens) {
        return imagens.stream().filter(i -> i.getAlternativaIndice() == null).collect(Collectors.toList());
    }

    /**
     * Converte lista de QuestaoImagem para ImagemDTO
     * Gera URL pública dinamicamente baseado no nome do arquivo
     */
    private List<QuestaoComAlternativasDTO.ImagemDTO> converterImagensParaDTO(List<QuestaoImagem> imagens) {
        return imagens.stream()
            .map(img -> QuestaoComAlternativasDTO.ImagemDTO.builder()
                .id(img.getId())
                .urlPublica("/api/imagens/questao/" + img.getNomeArquivo())
                .nomeArquivo(img.getNomeArquivo())
                .tipoMime(img.getTipoMime())
                .ordem(img.getOrdem())
                .textoOcr(img.getTextoOcr())
                .exibirNoEnunciado(img.getExibirNoEnunciado() != null ? img.getExibirNoEnunciado() : true)
                .exibirNasAlternativas(img.getExibirNasAlternativas() != null ? img.getExibirNasAlternativas() : false)
                .alternativaIndice(img.getAlternativaIndice())
                .build())
            .collect(Collectors.toList());
    }
}
