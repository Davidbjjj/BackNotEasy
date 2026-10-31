package com.example.BancoDeDados.ResponseDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ListaAtividadeDTO {
    private UUID eventoId;  // ID do evento associado à lista
    private UUID listaId;   // ID da lista
    private String titulo;
    private Integer totalQuestoes;
}

