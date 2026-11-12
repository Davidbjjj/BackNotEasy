package com.example.BancoDeDados.Services;

import com.example.BancoDeDados.Model.Notificacao;
import com.example.BancoDeDados.Model.TipoNotificacao;
import com.example.BancoDeDados.Repositores.NotificacaoRepository;
import com.example.BancoDeDados.ResponseDTO.NotificacaoDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collection;
import java.util.UUID;

@Service
public class NotificacaoService {

    private static final int MAX_PAGE_SIZE = 50;

    @Autowired
    private NotificacaoRepository repository;

    @Transactional(readOnly = true)
    public Page<NotificacaoDTO> listar(UUID usuarioId, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        PageRequest pr = PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        return repository.findByUsuarioId(usuarioId, pr).map(NotificacaoDTO::de);
    }

    @Transactional(readOnly = true)
    public long contarNaoLidas(UUID usuarioId) {
        return repository.countByUsuarioIdAndLidaFalse(usuarioId);
    }

    /** Idempotente. 404 se a notificação não for do usuário. */
    @Transactional
    public void marcarComoLida(UUID usuarioId, UUID id) {
        Notificacao n = repository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notificação não encontrada"));
        if (!n.isLida()) {
            n.setLida(true);
            repository.save(n);
        }
    }

    @Transactional
    public void marcarTodasComoLidas(UUID usuarioId) {
        repository.marcarTodasComoLidas(usuarioId);
    }

    @Transactional
    public void criar(UUID usuarioId, TipoNotificacao tipo, String titulo, String mensagem, String link) {
        if (usuarioId == null) return;
        Notificacao n = new Notificacao();
        n.setUsuarioId(usuarioId);
        n.setTipo(tipo);
        n.setTitulo(truncar(titulo, 120));
        n.setMensagem(truncar(mensagem, 500));
        n.setLink(link);
        repository.save(n);
    }

    @Transactional
    public void criarParaVarios(Collection<UUID> usuarioIds, TipoNotificacao tipo, String titulo, String mensagem, String link) {
        usuarioIds.forEach(id -> criar(id, tipo, titulo, mensagem, link));
    }

    /** Evita duplicar o mesmo aviso (ex.: lembrete de prazo) para o mesmo link. */
    @Transactional
    public void criarSeNaoExistir(UUID usuarioId, TipoNotificacao tipo, String titulo, String mensagem, String link) {
        if (usuarioId == null || repository.existsByUsuarioIdAndTipoAndLink(usuarioId, tipo, link)) return;
        criar(usuarioId, tipo, titulo, mensagem, link);
    }

    private static String truncar(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
