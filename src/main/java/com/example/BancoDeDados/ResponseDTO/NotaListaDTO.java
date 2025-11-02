package com.example.BancoDeDados.ResponseDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotaListaDTO {
    private UUID listaId;
    private String tituloLista;
    private UUID estudanteId;
    private String nomeEstudante;
    private Double nota;
    private Double porcentagemAcertos;
    private Integer questõesRespondidas;
    private Integer questõesCorretas;
    private Integer totalQuestoes;
}
