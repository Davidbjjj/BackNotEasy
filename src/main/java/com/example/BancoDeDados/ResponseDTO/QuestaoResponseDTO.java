package com.example.BancoDeDados.ResponseDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class QuestaoResponseDTO {
    private Integer id;
    private String cabecalho;
    private String enunciado;
    private List<String> alternativas;
    private Integer gabarito;

}
