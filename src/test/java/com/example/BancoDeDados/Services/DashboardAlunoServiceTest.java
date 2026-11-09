package com.example.BancoDeDados.Services;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DashboardAlunoServiceTest {

    private static final ZoneId SP = ZoneId.of("America/Sao_Paulo"); // UTC-3
    // 2026-11-08 12:00 em São Paulo
    private static final Instant AGORA = Instant.parse("2026-11-08T15:00:00Z");

    @Test
    void sequenciaZeroSemAtividade() {
        assertEquals(0, DashboardAlunoService.sequenciaAtual(List.of(), SP, AGORA));
    }

    @Test
    void sequenciaContaDiasSeguidosIncluindoHoje() {
        var instantes = List.of(
                Instant.parse("2026-11-08T13:00:00Z"),
                Instant.parse("2026-11-07T20:00:00Z"),
                Instant.parse("2026-11-06T10:00:00Z"));
        assertEquals(3, DashboardAlunoService.sequenciaAtual(instantes, SP, AGORA));
    }

    @Test
    void sequenciaContinuaVivaSeSoTeveAtividadeOntem() {
        var instantes = List.of(Instant.parse("2026-11-07T20:00:00Z"), Instant.parse("2026-11-06T20:00:00Z"));
        assertEquals(2, DashboardAlunoService.sequenciaAtual(instantes, SP, AGORA));
    }

    @Test
    void sequenciaQuebraComBuraco() {
        var instantes = List.of(Instant.parse("2026-11-08T13:00:00Z"), Instant.parse("2026-11-06T13:00:00Z"));
        assertEquals(1, DashboardAlunoService.sequenciaAtual(instantes, SP, AGORA));
    }

    @Test
    void sequenciaZeraSeUltimaAtividadeFoiAnteontem() {
        assertEquals(0, DashboardAlunoService.sequenciaAtual(
                List.of(Instant.parse("2026-11-06T13:00:00Z")), SP, AGORA));
    }

    @Test
    void sequenciaRespeitaOFusoNaViradaDoDia() {
        // 02:00Z de 08/11 ainda é 07/11 em São Paulo (23:00), mas já é 08/11 em Tóquio.
        var instantes = List.of(Instant.parse("2026-11-08T02:00:00Z"));
        Instant agoraSp = Instant.parse("2026-11-08T15:00:00Z");
        assertEquals(1, DashboardAlunoService.sequenciaAtual(instantes, SP, agoraSp)); // ontem conta
        assertEquals(1, DashboardAlunoService.sequenciaAtual(instantes, ZoneId.of("Asia/Tokyo"), agoraSp));
        // dois instantes no mesmo dia UTC mas em dias diferentes em SP => 2 dias seguidos
        var doisDias = List.of(Instant.parse("2026-11-08T02:00:00Z"), Instant.parse("2026-11-08T14:00:00Z"));
        assertEquals(2, DashboardAlunoService.sequenciaAtual(doisDias, SP, agoraSp));
    }

    @Test
    void fusoHeaderVenceFusoDoAlunoQueVenceOPadrao() {
        assertEquals(ZoneId.of("Asia/Tokyo"), DashboardAlunoService.resolverFuso("Asia/Tokyo", "Europe/Lisbon"));
        assertEquals(ZoneId.of("Europe/Lisbon"), DashboardAlunoService.resolverFuso(null, "Europe/Lisbon"));
        assertEquals(ZoneId.of("Europe/Lisbon"), DashboardAlunoService.resolverFuso("  ", "Europe/Lisbon"));
        assertEquals(DashboardAlunoService.FUSO_PADRAO, DashboardAlunoService.resolverFuso(null, null));
        assertEquals(DashboardAlunoService.FUSO_PADRAO, DashboardAlunoService.resolverFuso(null, "lixo"));
    }

    @Test
    void fusoInvalidoNoHeaderEhErro() {
        assertThrows(IllegalArgumentException.class, () -> DashboardAlunoService.resolverFuso("Marte/Olympus", null));
    }
}
