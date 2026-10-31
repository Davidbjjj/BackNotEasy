package com.example.BancoDeDados.Repositores;

import com.example.BancoDeDados.Model.Estudante;
import com.example.BancoDeDados.Model.Evento;
import com.example.BancoDeDados.Model.NotaEvento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotaEventoRepository extends JpaRepository<NotaEvento, UUID> {


    Optional<NotaEvento> findByEstudanteAndEvento(Estudante estudante, Evento evento);
    @Query("SELECT ne FROM NotaEvento ne WHERE ne.estudante.id = :estudanteId AND ne.evento.id = :eventoId")
    Optional<NotaEvento> findByEstudanteIdAndEventoId(@Param("estudanteId") UUID estudanteId,
                                                      @Param("eventoId") UUID eventoId);
//    Optional<NotaEvento> findByEstudanteAndMateriaAndTime(Estudante estudante, Materia materia, Long time);

    List<NotaEvento> findByEvento(Evento evento);

    @Query("SELECT n FROM NotaEvento n " +
           "JOIN n.evento e " +
           "LEFT JOIN e.disciplina m " +
           "WHERE e.data BETWEEN :startDate AND :endDate " +
           "AND (:disciplinaId IS NULL OR m.id = :disciplinaId)")
    List<NotaEvento> findByPeriodoAndDisciplina(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param(value = "disciplinaId") UUID disciplinaId
    );

    // Buscar notas de eventos de um estudante em uma disciplina específica
    @Query("SELECT ne FROM NotaEvento ne WHERE ne.estudante.id = :estudanteId AND ne.evento.disciplina.id = :disciplinaId")
    List<NotaEvento> findByEstudanteIdAndEventoDisciplinaId(@Param("estudanteId") UUID estudanteId, @Param("disciplinaId") UUID disciplinaId);
}
