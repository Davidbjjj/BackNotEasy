package com.example.BancoDeDados.ResponseDTO;

import lombok.Data;
import java.util.UUID;

@Data
public class ListaEventoResponse {
    private UUID id;
    private UUID listaId;
    private String listaTitulo;
    private UUID eventoId;
    private String eventoTitulo;
}