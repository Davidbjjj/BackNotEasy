package com.example.BancoDeDados.Repositores;

import com.example.BancoDeDados.Model.Estudante;
import com.example.BancoDeDados.Model.Questao;
import com.example.BancoDeDados.Model.RespostaEstudantes;
import com.example.BancoDeDados.ResponseDTO.AlunoMediaDTO;
import com.example.BancoDeDados.ResponseDTO.ListaMediaDTO;
import com.example.BancoDeDados.ResponseDTO.AtividadeConcluidaDTO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RespostaEstudantesRepository extends JpaRepository<RespostaEstudantes, Long> {

    // Métodos básicos (mantenha estes)
    Optional<RespostaEstudantes> findByQuestaoIdAndEstudanteId(Integer questaoId, UUID estudanteId);
    boolean existsByEstudanteId(UUID estudanteId);
    Optional<RespostaEstudantes> findByQuestaoAndEstudante(Questao questao, Estudante estudante);
    boolean existsByQuestaoIdAndEstudanteId(Integer questaoId, UUID estudanteId);
    List<RespostaEstudantes> findByEstudanteId(UUID estudanteId);

    // CORREÇÃO: Buscar questões respondidas por estudante e lista
    @Query("SELECT r.questao.id FROM RespostaEstudantes r " +
            "WHERE r.estudante.id = :estudanteId " +
            "AND r.questao.lista.id = :listaId")
    List<Integer> findQuestoesRespondidasByEstudanteAndLista(@Param("estudanteId") UUID estudanteId,
                                                             @Param("listaId") UUID listaId);

    // CORREÇÃO: Buscar respostas por estudante e lista
    @Query("SELECT re FROM RespostaEstudantes re " +
            "WHERE re.estudante.id = :estudanteId " +
            "AND re.questao.lista.id = :listaId")
    List<RespostaEstudantes> findByEstudanteIdAndQuestaoListaId(@Param("estudanteId") UUID estudanteId,
                                                                @Param("listaId") UUID listaId);

    // CORREÇÃO: Contar respostas corretas por estudante e lista
    @Query("SELECT COUNT(re) FROM RespostaEstudantes re " +
            "WHERE re.estudante.id = :estudanteId " +
            "AND re.questao.lista.id = :listaId " +
            "AND re.resposta = true")
    long countByEstudanteIdAndQuestaoListaIdAndRespostaTrue(@Param("estudanteId") UUID estudanteId,
                                                            @Param("listaId") UUID listaId);

    // CORREÇÃO: Método alternativo usando JOIN explícito
    @Query("SELECT re FROM RespostaEstudantes re " +
            "JOIN re.questao q " +
            "WHERE re.estudante.id = :estudanteId AND q.lista.id = :listaId")
    List<RespostaEstudantes> findRespostasPorEstudanteELista(@Param("estudanteId") UUID estudanteId,
                                                             @Param("listaId") UUID listaId);

    // CORREÇÃO: Buscar respostas por lista
    @Query("SELECT re FROM RespostaEstudantes re " +
            "WHERE re.questao.lista.id = :listaId")
    List<RespostaEstudantes> findByQuestaoListaId(@Param("listaId") UUID listaId);

    // CORREÇÃO: Buscar respostas com JOIN FETCH
    @Query("SELECT re FROM RespostaEstudantes re " +
            "JOIN FETCH re.questao q " +
            "JOIN FETCH re.estudante e " +
            "WHERE q.lista.id = :listaId")
    List<RespostaEstudantes> findByListaIdWithJoins(@Param("listaId") UUID listaId);

    // Este método está correto (mantenha)
    @Query("SELECT re FROM RespostaEstudantes re JOIN FETCH re.questao WHERE re.estudante.id = :estudanteId AND re.questao.id IN :questaoIds")
    List<RespostaEstudantes> findByEstudanteIdAndQuestaoIdIn(@Param("estudanteId") UUID estudanteId,
                                                             @Param("questaoIds") List<Integer> questaoIds);

    // New: per-student average nota and responses count for a disciplina
    @Query("SELECT new com.example.BancoDeDados.ResponseDTO.AlunoMediaDTO(e.id, e.nome, AVG(CASE WHEN (re.resposta = true) THEN 1.0 ELSE 0 END), COUNT(re)) " +
            "FROM RespostaEstudantes re JOIN re.estudante e JOIN re.questao q JOIN q.lista l JOIN l.disciplina d " +
            "WHERE d.id = :disciplinaId " +
            "GROUP BY e.id, e.nome " +
            "ORDER BY AVG(CASE WHEN (re.resposta = true) THEN 1.0 ELSE 0 END) DESC")
    List<AlunoMediaDTO> findAlunoMediasByDisciplina(@Param("disciplinaId") UUID disciplinaId);

    // New: per-list average correctness (0..1) and responses count for a disciplina
    @Query("SELECT new com.example.BancoDeDados.ResponseDTO.ListaMediaDTO(l.id, l.titulo, AVG(CASE WHEN (re.resposta = true) THEN 1.0 ELSE 0 END), COUNT(re)) " +
            "FROM RespostaEstudantes re JOIN re.questao q JOIN q.lista l JOIN l.disciplina d " +
            "WHERE d.id = :disciplinaId " +
            "GROUP BY l.id, l.titulo " +
            "ORDER BY AVG(CASE WHEN (re.resposta = true) THEN 1.0 ELSE 0 END) ASC")
    List<ListaMediaDTO> findListaMediasByDisciplinaOrderByMediaAsc(@Param("disciplinaId") UUID disciplinaId);

    // New: atividades concluídas per student (count distinct listas with at least one resposta) in disciplina
    @Query("SELECT new com.example.BancoDeDados.ResponseDTO.AtividadeConcluidaDTO(e.id, e.nome, d.id, d.nome, COUNT(DISTINCT l.id)) " +
            "FROM RespostaEstudantes re JOIN re.estudante e JOIN re.questao q JOIN q.lista l JOIN l.disciplina d " +
            "WHERE d.id = :disciplinaId " +
            "GROUP BY e.id, e.nome, d.id, d.nome " +
            "ORDER BY COUNT(DISTINCT l.id) DESC")
    List<AtividadeConcluidaDTO> findAtividadesConcluidasByDisciplina(@Param("disciplinaId") UUID disciplinaId);

}
