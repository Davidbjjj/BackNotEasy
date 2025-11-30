package com.example.BancoDeDados.Repositores;

import com.example.BancoDeDados.Model.Lista;
import com.example.BancoDeDados.Model.Professor;
import com.example.BancoDeDados.Repositores.projections.PiorListaProjection;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ListaRepository extends JpaRepository<Lista, UUID> {
    List<Lista> findByProfessorId(UUID professorId);

    @Query("SELECT l FROM Lista l JOIN l.estudantes e WHERE e.id = :estudanteId")
    List<Lista> findByEstudanteId(@Param("estudanteId") UUID estudanteId);

    @Query("SELECT l FROM Lista l WHERE l.disciplina.id = :disciplinaId")
    List<Lista> findByDisciplinaId(@Param("disciplinaId") UUID disciplinaId);

    @Query("SELECT l FROM Lista l WHERE l.disciplina.instituicao.id = :instituicaoId")
    List<Lista> findByDisciplina_Instituicao_Id(@Param("instituicaoId") UUID instituicaoId);

    @Query("SELECT DISTINCT l FROM Lista l LEFT JOIN FETCH l.questoes WHERE l.id = :id")
    Optional<Lista> findByIdWithQuestoes(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"questoes"})
    Optional<Lista> findWithQuestoesById(UUID id);

    @Query("SELECT COUNT(q) FROM Questao q WHERE q.lista.id = :listaId")
    int countQuestoesNaLista(@Param("listaId") UUID listaId);

    @Query("SELECT q.id FROM Questao q WHERE q.lista.id = :listaId")
    List<Integer> findQuestaoIdsByListaId(@Param("listaId") UUID listaId);

    @Query("SELECT CASE WHEN COUNT(q) > 0 THEN true ELSE false END FROM Questao q WHERE q.id = :questaoId AND q.lista.id = :listaId")
    boolean existsQuestaoInLista(@Param("questaoId") Integer questaoId, @Param("listaId") UUID listaId);

    @Query("SELECT DISTINCT l FROM Lista l LEFT JOIN FETCH l.questoes LEFT JOIN FETCH l.estudantes WHERE l.id = :listaId")
    Optional<Lista> findByIdWithQuestoesAndEstudantes(@Param("listaId") UUID listaId);

    @Query(value = "SELECT l.id as listaId, l.titulo as titulo, COUNT(re.id) as totalRespostas, " +
            "SUM(CASE WHEN re.resposta = false THEN 1 ELSE 0 END) as erros, " +
            "SUM(CASE WHEN re.resposta = true THEN 1 ELSE 0 END) as acertos " +
            "FROM listas l " +
            "LEFT JOIN questao q ON q.lista_id = l.id " +
            "LEFT JOIN resposta_estudantes re ON re.questao_id = q.id " +
            "WHERE l.disciplina_id = :disciplinaId " +
            "GROUP BY l.id " +
            "ORDER BY CASE WHEN COUNT(re.id)=0 THEN 0 ELSE SUM(CASE WHEN re.resposta = false THEN 1 ELSE 0 END)::float / COUNT(re.id) END DESC " +
            "LIMIT 10", nativeQuery = true)
    List<PiorListaProjection> findTop10PioresListasByDisciplina(@Param("disciplinaId") UUID disciplinaId);
}