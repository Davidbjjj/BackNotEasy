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
public class RespostasListaComNotaDTO {
    private UUID listaId;
    private String tituloLista;
    private List<RespostaEstudanteQuestaoDTO> respostas;
    private BigDecimal notaLista; // Novo campo
    private BigDecimal porcentagemAcertos; // Opcional
    private Integer totalQuestoes;
    private Integer questõesRespondidas;
    private Integer questõesCorretas;
}