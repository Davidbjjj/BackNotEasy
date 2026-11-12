package com.example.BancoDeDados.Controller;

import com.example.BancoDeDados.ResponseDTO.NotificacaoDTO;
import com.example.BancoDeDados.Security.CallerContext;
import com.example.BancoDeDados.Services.NotificacaoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Notificações do usuário autenticado (docs/notifications-api.md do front). */
@RestController
@RequestMapping("/notificacoes")
public class NotificacaoController {

    @Autowired
    private NotificacaoService service;

    private static UUID usuarioAutenticado() {
        if (!CallerContext.isAuthenticated() || CallerContext.userId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Autenticação necessária");
        }
        return CallerContext.userId();
    }

    @GetMapping
    public Map<String, Object> listar(@RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "10") int size) {
        Page<NotificacaoDTO> p = service.listar(usuarioAutenticado(), page, size);
        List<NotificacaoDTO> items = p.getContent();
        return Map.of(
                "items", items,
                "page", p.getNumber(),
                "size", p.getSize(),
                "totalElements", p.getTotalElements(),
                "totalPages", p.getTotalPages());
    }

    @GetMapping("/nao-lidas/contagem")
    public Map<String, Long> contagem() {
        return Map.of("count", service.contarNaoLidas(usuarioAutenticado()));
    }

    @PatchMapping("/{id}/lida")
    public ResponseEntity<Void> marcarLida(@PathVariable UUID id) {
        service.marcarComoLida(usuarioAutenticado(), id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/lidas")
    public ResponseEntity<Void> marcarTodasLidas() {
        service.marcarTodasComoLidas(usuarioAutenticado());
        return ResponseEntity.noContent().build();
    }
}
