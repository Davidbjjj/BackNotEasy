package com.example.BancoDeDados.Repositores;

import com.example.BancoDeDados.Model.Estudante;
import com.example.BancoDeDados.Model.Evento;
import com.example.BancoDeDados.Model.Materia;
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

    Optional<NotaEvento> findByEstudanteAndMateriaAndTime(Estudante estudante, Materia materia, Long time);

    List<NotaEvento> findByEvento(Evento evento);

    @Query("SELECT n FROM NotaEvento n " +
           "JOIN n.evento e " +
           "LEFT JOIN e.materia m " +
           "WHERE e.data BETWEEN :startDate AND :endDate " +
           "AND (:materiaId IS NULL OR m.id = :materiaId)")
    List<NotaEvento> findByPeriodoAndMateria(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param(value = "materiaId") UUID materiaId
    );
}
