package com.example.BancoDeDados.ResponseDTO;

import java.util.List;
import java.util.UUID;

public class AnaliseIAListaDTO {
    private UUID listaId;
    private String titulo;
    private String sugestao;
    private List<String> pontosPrincipais;

    public AnaliseIAListaDTO() {}

    public AnaliseIAListaDTO(UUID listaId, String titulo, String sugestao, List<String> pontosPrincipais) {
        this.listaId = listaId;
        this.titulo = titulo;
        this.sugestao = sugestao;
        this.pontosPrincipais = pontosPrincipais;
    }

    public UUID getListaId() { return listaId; }
    public void setListaId(UUID listaId) { this.listaId = listaId; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getSugestao() { return sugestao; }
    public void setSugestao(String sugestao) { this.sugestao = sugestao; }
    public List<String> getPontosPrincipais() { return pontosPrincipais; }
    public void setPontosPrincipais(List<String> pontosPrincipais) { this.pontosPrincipais = pontosPrincipais; }
}

