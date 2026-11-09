package com.example.BancoDeDados.Repositores.projections;

import java.time.Instant;
import java.util.UUID;

/** Última resposta (atualizadoEm mais recente) de um aluno em uma lista. */
public interface AtividadeListaProjection {
    UUID getListaId();

    Instant getUltimaAtividade();
}
