package com.example.BancoDeDados.ResponseDTO;

import java.util.List;
import java.util.UUID;

public class ListaTopDTO {
    private UUID listaId;
    private String titulo;
    private List<QuestaoStatsDTO> questoes;

    public ListaTopDTO() {}

    public ListaTopDTO(UUID listaId, String titulo, List<QuestaoStatsDTO> questoes) {
        this.listaId = listaId;
        this.titulo = titulo;
        this.questoes = questoes;
    }

    public UUID getListaId() { return listaId; }
    public void setListaId(UUID listaId) { this.listaId = listaId; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public List<QuestaoStatsDTO> getQuestoes() { return questoes; }
    public void setQuestoes(List<QuestaoStatsDTO> questoes) { this.questoes = questoes; }
}

