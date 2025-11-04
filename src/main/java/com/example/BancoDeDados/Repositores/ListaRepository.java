package com.example.BancoDeDados.Repositores;

import com.example.BancoDeDados.Model.Lista;
import com.example.BancoDeDados.Model.Professor;
import org.springframework.data.jpa.repository.EntityGraph;
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

    @Query("SELECT l FROM Lista l JOIN l.estudantes e WHERE e.id = :estudanteId")
    List<Lista> findByEstudanteId(@Param("estudanteId") UUID estudanteId);

     @Query("SELECT l FROM Lista l WHERE l.disciplina.id = :disciplinaId")
     List<Lista> findByDisciplinaId(@Param("disciplinaId") UUID disciplinaId);

    // Método para buscar lista com questões carregadas (EAGER)

    // Método alternativo se o acima não funcionar
    @Query("SELECT l FROM Lista l LEFT JOIN FETCH l.questoes q WHERE l.id = :listaId")
    Optional<Lista> findByIdWithQuestoesAlternative(@Param("listaId") UUID listaId);

    // Método usando EntityGraph
    @EntityGraph(attributePaths = {"questoes"})
    Optional<Lista> findWithQuestoesById(UUID id);
    // Método para contar quantas questões estão associadas à lista
    @Query(value = "SELECT COUNT(*) FROM lista_questoes WHERE lista_id = :listaId", nativeQuery = true)
    int countQuestoesNaLista(@Param("listaId") UUID listaId);

    // Método para verificar registros específicos na tabela de junção
    @Query(value = "SELECT * FROM lista_questoes WHERE lista_id = :listaId", nativeQuery = true)
    List<Object[]> findRegistrosJuncao(@Param("listaId") UUID listaId);
}
