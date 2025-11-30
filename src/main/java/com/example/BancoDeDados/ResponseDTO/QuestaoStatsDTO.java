package com.example.BancoDeDados.ResponseDTO;

public class QuestaoStatsDTO {
    private Integer questaoId;
    private String enunciado;
    private String gabarito;
    private Integer totalRespondidas;
    private Integer acertos;
    private Integer erros;

    public QuestaoStatsDTO() {}

    public QuestaoStatsDTO(Integer questaoId, String enunciado, String gabarito,
                           Integer totalRespondidas, Integer acertos, Integer erros) {
        this.questaoId = questaoId;
        this.enunciado = enunciado;
        this.gabarito = gabarito;
        this.totalRespondidas = totalRespondidas;
        this.acertos = acertos;
        this.erros = erros;
    }

    public Integer getQuestaoId() { return questaoId; }
    public void setQuestaoId(Integer questaoId) { this.questaoId = questaoId; }
    public String getEnunciado() { return enunciado; }
    public void setEnunciado(String enunciado) { this.enunciado = enunciado; }
    public String getGabarito() { return gabarito; }
    public void setGabarito(String gabarito) { this.gabarito = gabarito; }
    public Integer getTotalRespondidas() { return totalRespondidas; }
    public void setTotalRespondidas(Integer totalRespondidas) { this.totalRespondidas = totalRespondidas; }
    public Integer getAcertos() { return acertos; }
    public void setAcertos(Integer acertos) { this.acertos = acertos; }
    public Integer getErros() { return erros; }
    public void setErros(Integer erros) { this.erros = erros; }
}

