package com.example.BancoDeDados.Repositores;

import com.example.BancoDeDados.Model.Estudante;
import com.example.BancoDeDados.Model.Questao;
import com.example.BancoDeDados.Model.RespostaEstudantes;
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
}
