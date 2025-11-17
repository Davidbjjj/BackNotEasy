package com.example.BancoDeDados.Repositores;

import com.example.BancoDeDados.Model.QuestaoAlternativa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface QuestaoAlternativaRepository extends JpaRepository<QuestaoAlternativa, Long> {

    @Query("SELECT a FROM QuestaoAlternativa a WHERE a.questao.id = :questaoId ORDER BY a.ordem")
    List<QuestaoAlternativa> findByQuestaoIdOrderByOrdem(@Param("questaoId") Integer questaoId);

    @Query("SELECT a FROM QuestaoAlternativa a WHERE a.questao.id IN :questaoIds ORDER BY a.questao.id, a.ordem")
    List<QuestaoAlternativa> findByQuestaoIdInOrderByOrdem(@Param("questaoIds") List<Integer> questaoIds);
}

