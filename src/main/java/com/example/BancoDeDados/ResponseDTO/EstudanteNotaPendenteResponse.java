package com.example.BancoDeDados.ResponseDTO;

import lombok.Data;
import java.util.UUID;

@Data
public class EstudanteNotaPendenteResponse {
    private UUID estudanteId;
    private String estudanteNome;
    private UUID eventoId;
    private String eventoTitulo;
}
