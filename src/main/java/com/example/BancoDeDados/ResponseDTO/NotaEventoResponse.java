package com.example.BancoDeDados.ResponseDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
public class NotaEventoResponse {
    private UUID id;
    private String estudanteNome;  // antes alunoNome
    private UUID estudanteId;      // antes alunoId
    private String eventoTitulo;
    private UUID eventoId;
    private Double nota;
    private Double notaMaxima;
    private String observacao;
    private String professorNome;
    private String statusEntrega;
    private String comentarioEntrega;
    private List<String> arquivosEntrega;
}
