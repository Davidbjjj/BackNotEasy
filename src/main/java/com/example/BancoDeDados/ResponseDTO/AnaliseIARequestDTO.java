package com.example.BancoDeDados.ResponseDTO;

import java.util.List;
import java.util.UUID;

public class AnaliseIARequestDTO {
    private UUID disciplinaId;
    private List<ListaTopDTO> listas;

    public AnaliseIARequestDTO() {}

    public AnaliseIARequestDTO(UUID disciplinaId, List<ListaTopDTO> listas) {
        this.disciplinaId = disciplinaId;
        this.listas = listas;
    }

    public UUID getDisciplinaId() { return disciplinaId; }
    public void setDisciplinaId(UUID disciplinaId) { this.disciplinaId = disciplinaId; }
    public List<ListaTopDTO> getListas() { return listas; }
    public void setListas(List<ListaTopDTO> listas) { this.listas = listas; }
}

