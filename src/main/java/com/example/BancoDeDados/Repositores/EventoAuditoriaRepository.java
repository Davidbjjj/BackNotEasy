package com.example.BancoDeDados.Repositores;

import com.example.BancoDeDados.Model.EventoAuditoria;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface EventoAuditoriaRepository extends JpaRepository<EventoAuditoria, UUID> {
    List<EventoAuditoria> findByInstituicaoIdOrderByCriadoEmDesc(UUID instituicaoId);
}
