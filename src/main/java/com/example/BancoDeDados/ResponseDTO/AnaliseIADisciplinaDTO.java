package com.example.BancoDeDados.ResponseDTO;

import java.util.List;
import java.util.UUID;

public class AnaliseIADisciplinaDTO {
    private UUID disciplinaId;
    private String sugestao;
    private List<String> pontosPrincipais;

    public AnaliseIADisciplinaDTO() {}

    public AnaliseIADisciplinaDTO(UUID disciplinaId, String sugestao, List<String> pontosPrincipais) {
        this.disciplinaId = disciplinaId;
        this.sugestao = sugestao;
        this.pontosPrincipais = pontosPrincipais;
    }

    public UUID getDisciplinaId() { return disciplinaId; }
    public void setDisciplinaId(UUID disciplinaId) { this.disciplinaId = disciplinaId; }
    public String getSugestao() { return sugestao; }
    public void setSugestao(String sugestao) { this.sugestao = sugestao; }
    public List<String> getPontosPrincipais() { return pontosPrincipais; }
    public void setPontosPrincipais(List<String> pontosPrincipais) { this.pontosPrincipais = pontosPrincipais; }
}

