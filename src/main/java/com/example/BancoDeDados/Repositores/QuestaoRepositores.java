package com.example.BancoDeDados.Repositores;

import com.example.BancoDeDados.Model.Questao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuestaoRepositores extends JpaRepository<Questao,Integer> {
    List<Questao> findByLista_Id(UUID listaId);
    Optional<Questao> findById(Integer id);
}
