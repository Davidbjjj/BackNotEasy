package com.example.BancoDeDados.ResponseDTO;

import java.util.List;
import java.util.UUID;

public record RespostasListaDTO(
         UUID listaId,
       String tituloLista,
      List<RespostaEstudanteQuestaoDTO> respostas
) {}