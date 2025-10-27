package com.example.BancoDeDados.Repositores;

import com.example.BancoDeDados.Model.Lista;
import com.example.BancoDeDados.Model.Professor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ListaRepository extends JpaRepository<Lista, UUID> {
    List<Lista> findByProfessorId(UUID professorId);

    Optional<Professor> findByIdAndProfessor(UUID listaId, Professor professor);

    List<Lista> findAll();

    @Query("SELECT DISTINCT l FROM Lista l LEFT JOIN FETCH l.questoes WHERE l.id = :id")
    Optional<Lista> findByIdWithQuestoes(@Param("id") UUID id);
}
