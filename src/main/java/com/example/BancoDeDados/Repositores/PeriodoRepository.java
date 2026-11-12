package com.example.BancoDeDados.Repositores;

import com.example.BancoDeDados.Model.Periodo;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface PeriodoRepository extends JpaRepository<Periodo, UUID> {
    List<Periodo> findByInstituicaoIdOrderByInicioDesc(UUID instituicaoId);
}
