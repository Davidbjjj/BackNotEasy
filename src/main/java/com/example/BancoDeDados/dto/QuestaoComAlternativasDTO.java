package com.example.BancoDeDados.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestaoComAlternativasDTO {
    private Integer id;
    private String cabecalho;
    private String enunciado;
    private Integer gabarito;
    private List<AlternativaDTO> alternativas;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AlternativaDTO {
        private Long id;
        private Integer ordem;
        private String texto;
    }
}

