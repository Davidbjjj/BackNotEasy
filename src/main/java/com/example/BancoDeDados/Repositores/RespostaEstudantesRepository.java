package com.example.BancoDeDados.Repositores;

import com.example.BancoDeDados.Model.RespostaEstudantes;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RespostaEstudantesRepository extends JpaRepository<RespostaEstudantes, Long> {
    Optional<RespostaEstudantes> findByQuestaoIdAndEstudanteId(Integer questaoId, UUID estudanteId);

    boolean existsByEstudanteId(UUID estudanteId);
}
