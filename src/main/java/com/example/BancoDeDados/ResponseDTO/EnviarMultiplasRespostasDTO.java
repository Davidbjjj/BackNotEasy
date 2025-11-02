package com.example.BancoDeDados.ResponseDTO;

import java.util.List;
import java.util.UUID;

public record EnviarMultiplasRespostasDTO(
        UUID estudanteId,
        UUID listaId,
        List<RespostaQuestaoDTO> respostas
) {}
