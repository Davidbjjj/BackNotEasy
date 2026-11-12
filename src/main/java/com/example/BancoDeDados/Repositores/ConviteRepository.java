package com.example.BancoDeDados.Repositores;

import com.example.BancoDeDados.Model.Convite;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConviteRepository extends JpaRepository<Convite, UUID> {
    Optional<Convite> findByTokenHash(String tokenHash);
    List<Convite> findByInstituicaoIdOrderByCriadoEmDesc(UUID instituicaoId);
    List<Convite> findByInstituicaoIdAndEmailIgnoreCaseAndStatus(UUID instituicaoId, String email, String status);
}
