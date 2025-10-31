package com.example.BancoDeDados.ResponseDTO;

import lombok.Data;
import java.util.UUID;

@Data
public class ListaEventoRequest {
    private UUID listaId;
    private UUID eventoId;
}