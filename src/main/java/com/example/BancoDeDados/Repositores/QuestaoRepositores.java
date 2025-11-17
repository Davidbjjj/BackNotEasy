package com.example.BancoDeDados.Repositores;

import com.example.BancoDeDados.Model.Questao;
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
}
