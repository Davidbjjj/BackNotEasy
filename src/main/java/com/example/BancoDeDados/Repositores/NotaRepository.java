package com.example.BancoDeDados.Repositores;

import com.example.BancoDeDados.Model.NotaEvento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotaRepository extends JpaRepository<NotaEvento, Long> {
    @Query("SELECT AVG(n.nota) FROM NotaEvento n")
    Double calcularMediaGeral();

    @Query("SELECT n.evento.disciplina AS disciplina, AVG(n.nota) AS media " +
            "FROM NotaEvento n GROUP BY n.evento.disciplina ORDER BY media DESC")
    List<Object[]> rankingDisciplinas();

    @Query("SELECT a.nome, n.nota " +
            "FROM NotaEvento n JOIN n.estudante a " +
            "WHERE n.evento.disciplina.id = :id")
    List<Object[]> filtrarPorDisciplina(@Param("id") UUID disciplinaId);
}
