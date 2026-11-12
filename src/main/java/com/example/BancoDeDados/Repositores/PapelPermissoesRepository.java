package com.example.BancoDeDados.Repositores;

import com.example.BancoDeDados.Model.PapelPermissoes;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface PapelPermissoesRepository extends JpaRepository<PapelPermissoes, UUID> {
    Optional<PapelPermissoes> findByInstituicaoIdAndPapel(UUID instituicaoId, String papel);
}
