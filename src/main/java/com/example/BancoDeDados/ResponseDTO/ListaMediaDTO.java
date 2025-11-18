package com.example.BancoDeDados.ResponseDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ListaMediaDTO {
    private UUID listaId;
    private String listaTitulo;
    private Double media;
    private Long respostasCount;
}

