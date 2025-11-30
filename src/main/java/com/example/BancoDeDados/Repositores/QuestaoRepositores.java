package com.example.BancoDeDados.Repositores;

import com.example.BancoDeDados.Model.Questao;
import com.example.BancoDeDados.Repositores.projections.QuestaoStatsProjection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuestaoRepositores extends JpaRepository<Questao, Integer> {

    //  Query simples - SEM carregar alternativas (para listagens básicas)
    @Query("SELECT q FROM Questao q WHERE q.lista.id = :listaId ORDER BY q.id")
    List<Questao> findByListaId(@Param("listaId") UUID listaId);

    //  Compatibilidade com código antigo
    default List<Questao> findByLista_Id(UUID listaId) {
        return findByListaId(listaId);
    }

    //  Com JOIN FETCH apenas quando necessário (usa relacionamento @OneToMany)
    @Query("SELECT DISTINCT q FROM Questao q LEFT JOIN FETCH q.alternativas WHERE q.id = :id")
    Optional<Questao> findByIdWithAlternativas(@Param("id") Integer id);

    @Query("SELECT DISTINCT q FROM Questao q LEFT JOIN FETCH q.alternativas WHERE q.lista.id = :listaId ORDER BY q.id")
    List<Questao> findByListaIdWithAlternativas(@Param("listaId") UUID listaId);

    @Query("SELECT DISTINCT q FROM Questao q LEFT JOIN FETCH q.alternativas WHERE q.id IN :questaoIds ORDER BY q.id")
    List<Questao> findAllByIdWithAlternativas(@Param("questaoIds") List<Integer> questaoIds);

    @Query(value = "SELECT q.lista_id as listaId, q.id as questaoId, q.enunciado as enunciado, q.gabarito as gabarito, " +
            "COUNT(re.id) as totalRespondidas, SUM(CASE WHEN re.resposta = true THEN 1 ELSE 0 END) as acertos " +
            "FROM questao q LEFT JOIN resposta_estudantes re ON re.questao_id = q.id " +
            "WHERE q.lista_id IN :listaIds GROUP BY q.lista_id, q.id", nativeQuery = true)
    List<QuestaoStatsProjection> aggregateStatsByListaIds(@Param("listaIds") List<UUID> listaIds);
}
