package com.example.BancoDeDados.Repositores;

import com.example.BancoDeDados.Model.Notificacao;
import com.example.BancoDeDados.Model.TipoNotificacao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificacaoRepository extends JpaRepository<Notificacao, UUID> {

    Page<Notificacao> findByUsuarioId(UUID usuarioId, Pageable pageable);

    Optional<Notificacao> findByIdAndUsuarioId(UUID id, UUID usuarioId);

    long countByUsuarioIdAndLidaFalse(UUID usuarioId);

    boolean existsByUsuarioIdAndTipoAndLink(UUID usuarioId, TipoNotificacao tipo, String link);

    @Modifying
    @Query("update Notificacao n set n.lida = true where n.usuarioId = :usuarioId and n.lida = false")
    int marcarTodasComoLidas(@Param("usuarioId") UUID usuarioId);
}
