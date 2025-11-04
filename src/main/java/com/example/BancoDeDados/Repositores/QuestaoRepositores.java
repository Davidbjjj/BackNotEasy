package com.example.BancoDeDados.Repositores;

import com.example.BancoDeDados.Model.Questao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuestaoRepositores extends JpaRepository<Questao,Integer> {
    List<Questao> findByLista_Id(UUID listaId);

    Optional<Questao> findById(Integer Questao);

    // Buscar questão específica por lista
    @Query("SELECT q FROM Questao q WHERE q.id = :questaoId AND q.lista.id = :listaId")
    List<Questao> findByListaIdAndQuestaoId(@Param("listaId") UUID listaId, @Param("questaoId") Integer questaoId);

    // Buscar questões com alternativas carregadas
    @Query("SELECT DISTINCT q FROM Questao q LEFT JOIN FETCH q.alternativas WHERE q.lista.id = :listaId")
    List<Questao> findByListaIdWithAlternativas(@Param("listaId") UUID listaId);

    // Contar questões por lista
    long countByListaId(UUID listaId);
}
