package com.example.BancoDeDados.Services;

import com.example.BancoDeDados.Repositores.NotaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotaService {

    private final NotaRepository notaRepository;

    public Double calcularMediaGeral() {
        return notaRepository.calcularMediaGeral();
    }

    public List<Map<String, Object>> getRankingDisciplinas() {
        List<Object[]> dados = notaRepository.rankingDisciplinas();
        return dados.stream()
                .map(l -> Map.of("disciplina", l[0], "media", l[1]))
                .toList();
    }


    public List<Map<String, Object>> getPorDisciplina(UUID id) {
        List<Object[]> dados = notaRepository.filtrarPorDisciplina(id);
        return dados.stream()
                .map(l -> Map.of("aluno", l[0], "nota", l[1]))
                .toList();
    }
}