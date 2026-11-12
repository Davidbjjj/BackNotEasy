package com.example.BancoDeDados.ResponseDTO;

import com.example.BancoDeDados.Model.Notificacao;
import com.example.BancoDeDados.Model.TipoNotificacao;

import java.time.Instant;

/** Contrato do front (docs/notifications-api.md): id string, type/title/message/read/createdAt/link. */
public record NotificacaoDTO(
        String id,
        TipoNotificacao type,
        String title,
        String message,
        boolean read,
        Instant createdAt,
        String link
) {
    public static NotificacaoDTO de(Notificacao n) {
        return new NotificacaoDTO(n.getId().toString(), n.getTipo(), n.getTitulo(), n.getMensagem(),
                n.isLida(), n.getCreatedAt(), n.getLink());
    }
}
