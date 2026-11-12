package com.example.BancoDeDados.Repositores;

import com.example.BancoDeDados.Model.Turma;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface TurmaRepository extends JpaRepository<Turma, UUID> {
    List<Turma> findByInstituicaoIdOrderByNomeAsc(UUID instituicaoId);
    List<Turma> findByPeriodoIdOrderByNomeAsc(UUID periodoId);
}
