package com.example.BancoDeDados.ResponseDTO;

import lombok.Data;
import java.util.UUID;

@Data
public class NotaEventoRequest {
    private UUID estudanteId;
    private UUID eventoId;
    private Double nota;
    private String observacao;
}
