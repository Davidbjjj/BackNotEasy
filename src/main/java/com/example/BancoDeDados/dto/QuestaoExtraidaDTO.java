package com.example.BancoDeDados.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO representando uma questão extraída de documento
 */
public class QuestaoExtraidaDTO {
    private Integer numero;
    private String enunciado;
    private List<AlternativaDTO> alternativas = new ArrayList<>();
    private String contexto; // Texto antes do enunciado (se houver)
    private String gabarito; // Letra da resposta correta (se identificada)
    private Double confianca; // Nível de confiança da extração (0-1)

    public static class AlternativaDTO {
        private String letra;
        private String texto;

        public AlternativaDTO() {}

        public AlternativaDTO(String letra, String texto) {
            this.letra = letra;
            this.texto = texto;
        }

        public String getLetra() { return letra; }
        public void setLetra(String letra) { this.letra = letra; }
        public String getTexto() { return texto; }
        public void setTexto(String texto) { this.texto = texto; }
    }

    public QuestaoExtraidaDTO() {}

    public Integer getNumero() { return numero; }
    public void setNumero(Integer numero) { this.numero = numero; }

    public String getEnunciado() { return enunciado; }
    public void setEnunciado(String enunciado) { this.enunciado = enunciado; }

    public List<AlternativaDTO> getAlternativas() { return alternativas; }
    public void setAlternativas(List<AlternativaDTO> alternativas) { this.alternativas = alternativas; }

    public void addAlternativa(String letra, String texto) {
        this.alternativas.add(new AlternativaDTO(letra, texto));
    }

    public String getContexto() { return contexto; }
    public void setContexto(String contexto) { this.contexto = contexto; }

    public String getGabarito() { return gabarito; }
    public void setGabarito(String gabarito) { this.gabarito = gabarito; }

    public Double getConfianca() { return confianca; }
    public void setConfianca(Double confianca) { this.confianca = confianca; }
}

