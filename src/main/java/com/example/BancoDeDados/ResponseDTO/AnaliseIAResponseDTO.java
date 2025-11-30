package com.example.BancoDeDados.ResponseDTO;

import java.util.List;

public class AnaliseIAResponseDTO {
    private String sugestao;
    private List<String> pontosPrincipais;

    public AnaliseIAResponseDTO() {}

    public AnaliseIAResponseDTO(String sugestao, List<String> pontosPrincipais) {
        this.sugestao = sugestao;
        this.pontosPrincipais = pontosPrincipais;
    }

    public String getSugestao() { return sugestao; }
    public void setSugestao(String sugestao) { this.sugestao = sugestao; }
    public List<String> getPontosPrincipais() { return pontosPrincipais; }
    public void setPontosPrincipais(List<String> pontosPrincipais) { this.pontosPrincipais = pontosPrincipais; }
}
