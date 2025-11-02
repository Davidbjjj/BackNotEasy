package com.example.BancoDeDados.ResponseDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RespostasListaComEstatisticasDTO {
    private UUID listaId;
    private String tituloLista;
    private List<RespostaEstudanteQuestaoDTO> respostas;
    private BigDecimal notaMediaGeral; // Média das notas dos estudantes
    private BigDecimal porcentagemAcertosGeral; // Porcentagem geral de acertos
    private Integer totalQuestoes;
    private Integer totalRespostas;
    private Integer totalAcertos;
    private Integer totalEstudantes;
}