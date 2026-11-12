package com.example.BancoDeDados.ResponseDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuestaoRequestDTO {
    private String cabecalho;
    private String enunciado;
    private List<String> alternativas;
    private Integer gabarito;
    private String assunto;
    private String explicacao;
    /** "OBJETIVA" (padrão) ou "ABERTA". */
    private String tipo;
    private String rubrica;
    /** "FACIL" | "MEDIA" | "DIFICIL". */
    private String dificuldade;
    private java.util.List<String> tags;

}