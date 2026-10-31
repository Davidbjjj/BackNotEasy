package com.example.BancoDeDados.ResponseDTO;

import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class NotaEstudanteResponse {
    private UUID alunoId;
    private String alunoNome;
    private Double nota;
    private String observacao;
    private String statusEntrega;
    private String comentarioEntrega;
    private List<String> arquivosEntrega;
}
