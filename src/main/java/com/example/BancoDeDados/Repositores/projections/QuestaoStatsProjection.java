package com.example.BancoDeDados.Repositores.projections;

import java.util.UUID;

public interface QuestaoStatsProjection {
    UUID getListaId();
    Integer getQuestaoId();
    String getEnunciado();
    String getGabarito();
    Long getTotalRespondidas();
    Long getAcertos();
}

