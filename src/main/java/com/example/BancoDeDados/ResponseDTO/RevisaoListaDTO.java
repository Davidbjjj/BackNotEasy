package com.example.BancoDeDados.ResponseDTO;

import com.example.BancoDeDados.dto.QuestaoComAlternativasDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Revisão com gabarito: só é devolvida a aluno que finalizou, com gabarito liberado. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevisaoListaDTO {
    private UUID listaId;
    private String titulo;
    private BigDecimal nota;
    private BigDecimal porcentagemAcertos;
    private Integer totalQuestoes;
    private Integer questoesCorretas;
    private List<ItemRevisaoDTO> questoes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemRevisaoDTO {
        private Integer questaoId;
        /** Por ora só "OBJETIVA"; questões abertas dependem de decisão de modelagem. */
        private String tipo;
        private String assunto;
        private String cabecalho;
        private String enunciado;
        private List<QuestaoComAlternativasDTO.AlternativaDTO> alternativas;
        private List<QuestaoComAlternativasDTO.ImagemDTO> imagens;
        /** Índice marcado pelo aluno; null se não respondeu. */
        private Integer respostaAluno;
        private Integer gabarito;
        /** null quando a questão não tem gabarito cadastrado. */
        private Boolean correta;
        private String explicacao;
        /** Questão aberta (FE-201): texto do aluno, nota 0-10 e feedback do professor. */
        private String textoResposta;
        private BigDecimal notaQuestao;
        private String feedback;
        /** null em objetivas; false = ainda na fila de correção. */
        private Boolean corrigida;
    }
}
