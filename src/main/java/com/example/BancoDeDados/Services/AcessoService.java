package com.example.BancoDeDados.Services;

import com.example.BancoDeDados.Model.AcessoEstudante;
import com.example.BancoDeDados.Repositores.AcessoEstudanteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Registra os acessos do aluno ao app, no máximo um a cada 30 minutos (FE-204/207). */
@Service
public class AcessoService {

    static final Duration INTERVALO = Duration.ofMinutes(30);

    private final Map<UUID, Instant> ultimoRegistro = new ConcurrentHashMap<>();

    @Autowired
    private AcessoEstudanteRepository repository;

    public void registrar(UUID estudanteId) {
        Instant agora = Instant.now();
        Instant ultimo = ultimoRegistro.get(estudanteId);
        if (ultimo == null) {
            ultimo = repository.ultimoAcesso(estudanteId);
        }
        if (ultimo != null && ultimo.plus(INTERVALO).isAfter(agora)) {
            ultimoRegistro.put(estudanteId, ultimo);
            return;
        }
        repository.save(new AcessoEstudante(estudanteId, agora));
        ultimoRegistro.put(estudanteId, agora);
    }
}
