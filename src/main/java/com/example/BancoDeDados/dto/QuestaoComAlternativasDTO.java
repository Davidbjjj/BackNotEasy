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
    private List<ImagemDTO> imagens; // Adiciona lista de imagens

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AlternativaDTO {
        private Long id;
        private Integer ordem;
        private String texto;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImagemDTO {
        private Long id;
        private String urlPublica;
        private String nomeArquivo;
        private String tipoMime;
        private Integer ordem;
        private String textoOcr;
        private Boolean exibirNoEnunciado;       // true se deve exibir junto ao enunciado
        private Boolean exibirNasAlternativas;   // true se deve exibir junto às alternativas
    }
}

