package com.example.BancoDeDados.ResponseDTO;

import java.util.UUID;

public record RespostaEstudanteDTO(Long id,
                                   Integer questaoId,
                                   UUID estudanteId,
                                   Integer alternativa,
                                   Boolean correta ) {
}
