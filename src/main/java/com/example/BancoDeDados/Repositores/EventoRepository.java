package com.example.BancoDeDados.Repositores;

import com.example.BancoDeDados.Model.Evento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface EventoRepository extends JpaRepository<Evento, UUID> {

    // Buscar eventos de uma disciplina por faixa de data
    List<Evento> findByDataBetweenAndDisciplina_Id(LocalDateTime inicio, LocalDateTime fim, UUID disciplinaId);

    // Buscar eventos de uma disciplina pelo id
    List<Evento> findByDisciplinaId(UUID disciplinaId);

    // Buscar eventos em que um aluno está vinculado
    List<Evento> findByNotasEstudante_Estudante_Id(UUID estudanteId);

    // Buscar eventos de um professor pelo id
    List<Evento> findByProfessorId(UUID professorId);
}
