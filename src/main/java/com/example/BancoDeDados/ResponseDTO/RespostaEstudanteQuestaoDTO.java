package com.example.BancoDeDados.ResponseDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RespostaEstudanteQuestaoDTO {
    private Long respostaId;
    private Integer questaoId;
    private String enunciado; // Adicionado
    private UUID estudanteId;
    private String nomeEstudante;
    private Integer alternativa;
    private Boolean correta;

}