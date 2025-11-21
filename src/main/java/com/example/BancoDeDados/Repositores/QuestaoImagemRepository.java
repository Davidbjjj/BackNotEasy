package com.example.BancoDeDados.Repositores;

import com.example.BancoDeDados.Model.QuestaoImagem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuestaoImagemRepository extends JpaRepository<QuestaoImagem, Long> {

    List<QuestaoImagem> findByQuestaoIdOrderByOrdemAsc(Integer questaoId);

    void deleteByQuestaoId(Integer questaoId);
}

