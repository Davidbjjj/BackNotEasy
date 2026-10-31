package com.example.BancoDeDados.Repositores.projections;

import java.util.UUID;

public interface PiorListaProjection {
    UUID getListaId();
    String getTitulo();
    Long getTotalRespostas();
    Long getErros();
    Long getAcertos();
}

