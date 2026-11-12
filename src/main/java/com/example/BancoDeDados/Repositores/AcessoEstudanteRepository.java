package com.example.BancoDeDados.Repositores;

import com.example.BancoDeDados.Model.AcessoEstudante;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AcessoEstudanteRepository extends JpaRepository<AcessoEstudante, UUID> {

    @Query("SELECT MAX(a.acessoEm) FROM AcessoEstudante a WHERE a.estudanteId = :id")
    Instant ultimoAcesso(@Param("id") UUID id);

    @Query("SELECT a.estudanteId AS estudanteId, MAX(a.acessoEm) AS ultimo FROM AcessoEstudante a " +
            "WHERE a.estudanteId IN :ids GROUP BY a.estudanteId")
    List<UltimoAcesso> ultimosAcessos(@Param("ids") Collection<UUID> ids);

    /** FE-303: alunos com algum acesso no intervalo [ini, fim). */
    @Query("SELECT DISTINCT a.estudanteId FROM AcessoEstudante a WHERE a.estudanteId IN :ids " +
            "AND a.acessoEm >= :ini AND a.acessoEm < :fim")
    List<UUID> ativosNoPeriodo(@Param("ids") Collection<UUID> ids, @Param("ini") Instant ini, @Param("fim") Instant fim);

    List<AcessoEstudante> findByEstudanteIdOrderByAcessoEmDesc(UUID estudanteId, Pageable pageable);

    interface UltimoAcesso {
        UUID getEstudanteId();
        Instant getUltimo();
    }
}
