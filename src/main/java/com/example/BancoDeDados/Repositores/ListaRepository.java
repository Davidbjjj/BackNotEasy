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

    @Query("SELECT l FROM Lista l JOIN l.estudantes e WHERE e.id = :estudanteId")
    List<Lista> findByEstudanteId(@Param("estudanteId") UUID estudanteId);

    @Query("SELECT l FROM Lista l WHERE l.disciplina.id = :disciplinaId")
    List<Lista> findByDisciplinaId(@Param("disciplinaId") UUID disciplinaId);

    // Buscar listas de uma instituição através da disciplina
    @Query("SELECT l FROM Lista l WHERE l.disciplina.instituicao.id = :instituicaoId")
    List<Lista> findByDisciplina_Instituicao_Id(@Param("instituicaoId") UUID instituicaoId);

    // CORREÇÃO: Buscar lista com questões carregadas
    @Query("SELECT DISTINCT l FROM Lista l LEFT JOIN FETCH l.questoes WHERE l.id = :id")
    Optional<Lista> findByIdWithQuestoes(@Param("id") UUID id);

    // CORREÇÃO: Método alternativo
    @Query("SELECT l FROM Lista l LEFT JOIN FETCH l.questoes q WHERE l.id = :listaId")
    Optional<Lista> findByIdWithQuestoesAlternative(@Param("listaId") UUID listaId);

    // Método usando EntityGraph
    @EntityGraph(attributePaths = {"questoes"})
    Optional<Lista> findWithQuestoesById(UUID id);

    // CORREÇÃO: Contar questões na lista - use "Questao" (com Q maiúsculo)
    @Query("SELECT COUNT(q) FROM Questao q WHERE q.lista.id = :listaId")
    int countQuestoesNaLista(@Param("listaId") UUID listaId);

    // CORREÇÃO: Buscar questões por lista - use "Questao" (com Q maiúsculo)
    @Query("SELECT q.id FROM Questao q WHERE q.lista.id = :listaId")
    List<Integer> findQuestaoIdsByListaId(@Param("listaId") UUID listaId);

    // CORREÇÃO: Verificar se uma questão pertence a uma lista - use "Questao" (com Q maiúsculo)
    @Query("SELECT CASE WHEN COUNT(q) > 0 THEN true ELSE false END FROM Questao q WHERE q.id = :questaoId AND q.lista.id = :listaId")
    boolean existsQuestaoInLista(@Param("questaoId") Integer questaoId, @Param("listaId") UUID listaId);

    // CORREÇÃO: Buscar lista com questões e estudantes carregados
    @Query("SELECT DISTINCT l FROM Lista l LEFT JOIN FETCH l.questoes LEFT JOIN FETCH l.estudantes WHERE l.id = :listaId")
    Optional<Lista> findByIdWithQuestoesAndEstudantes(@Param("listaId") UUID listaId);
}