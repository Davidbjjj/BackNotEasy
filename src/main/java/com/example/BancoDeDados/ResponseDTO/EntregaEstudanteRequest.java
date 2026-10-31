package com.example.BancoDeDados.ResponseDTO;

import lombok.Data;
import java.util.List;

@Data
public class EntregaEstudanteRequest {
    private String statusEntrega;
    private String comentarioEntrega;
    private List<String> arquivosEntrega;
}

