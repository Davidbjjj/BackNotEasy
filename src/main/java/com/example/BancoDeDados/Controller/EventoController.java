package com.example.BancoDeDados.Controller;

import com.example.BancoDeDados.Model.Estudante;
import com.example.BancoDeDados.Model.Evento;
import com.example.BancoDeDados.Model.NotaEvento;
import com.example.BancoDeDados.ResponseDTO.*;
import com.example.BancoDeDados.Services.EventoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/eventos")
public class EventoController {

    @Autowired
    private EventoService service;

    @PostMapping("/notas")
    public ResponseEntity<NotaEventoResponse> adicionarOuAtualizarNotaEvento(@RequestBody NotaEventoRequest request) {
        NotaEventoResponse response = service.salvarNotaEvento(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{eventoId}/alunos/{alunoId}/avaliar")
    public ResponseEntity<NotaEventoResponse> avaliarAluno(
            @PathVariable UUID eventoId,
            @PathVariable UUID alunoId,
            @RequestBody AvaliacaoProfessorRequest avaliacaoRequest) {
        NotaEventoResponse response = service.avaliarEntrega(eventoId, alunoId, avaliacaoRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{eventoId}/alunos/{alunoId}/entregar")
    public ResponseEntity<NotaEventoResponse> entregarEvento(
            @PathVariable UUID eventoId,
            @PathVariable UUID alunoId,
            @RequestBody EntregaEstudanteRequest entregaRequest) {
        NotaEventoResponse response = service.atualizarEntregaEstudante(eventoId, alunoId, entregaRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EventoComNotasResponse criarEvento(@RequestBody EventoRequest dto) {
        return service.criarEvento(dto);
    }


    @PutMapping("/{eventoId}")
    public Evento atualizarEvento(
            @PathVariable UUID eventoId,
            @RequestBody EventoComNotasResponse dto) {
        return service.atualizarEvento(eventoId, dto);
    }

    @DeleteMapping("/{eventoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletarEvento(@PathVariable UUID eventoId) {
        service.deletarEvento(eventoId);
    }

    // ===========================
    // RELACIONAMENTO ESTUDANTE <-> EVENTO
    // ===========================

    @PostMapping("/{eventoId}/alunos/{alunoId}")
    public Evento adicionarAlunoNoEvento(@PathVariable UUID eventoId, @PathVariable UUID alunoId) {
        return service.adicionarEstudante(eventoId, alunoId);
    }

    @DeleteMapping("/{eventoId}/alunos/{alunoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removerAlunoDoEvento(@PathVariable UUID eventoId, @PathVariable UUID alunoId) {
        service.removerEstudante(eventoId, alunoId);
    }

    // ===========================
    // CONSULTAS
    // ===========================

    @GetMapping("/{eventoId}")
    public Evento buscarEventoPorId(@PathVariable UUID eventoId) {
        return service.buscarEventoPorId(eventoId);
    }

    @GetMapping("/disciplina/{disciplinaId}")
    public List<Evento> listarPorDisciplina(@PathVariable UUID disciplinaId) {
        return service.listarPorDisciplina(disciplinaId);
    }

    @GetMapping("/aluno/email/{alunoEmail}")
    public List<Evento> listarPorEstudanteEmail(@PathVariable String alunoEmail) {
        return service.listarPorEstudanteEmail(alunoEmail);
    }

    @GetMapping("/{eventoId}/notas")
    public ResponseEntity<List<NotaEvento>> listarNotasDoEvento(@PathVariable UUID eventoId) {
        List<NotaEvento> notas = service.listarNotasPorEvento(eventoId);
        return ResponseEntity.ok(notas);
    }

    @GetMapping("/data")
    public List<Evento> buscarEventosPorDataEDisciplina(
            @RequestParam LocalDate data,
            @RequestParam UUID disciplinaId) {
        return service.buscarEventosPorDataEDisciplina(data, disciplinaId);
    }

    @GetMapping("/{eventoId}/alunos")
    public List<Estudante> listarAlunosDoEvento(@PathVariable UUID eventoId) {
        return service.listarEstudantesDoEvento(eventoId);
    }

    @PostMapping("/{eventoId}/listas")
    public ResponseEntity<ListaEventoResponse> adicionarListaAoEvento(
            @PathVariable UUID eventoId,
            @RequestBody ListaEventoRequest request) {
        ListaEventoResponse response = service.adicionarListaAoEvento(eventoId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }


    @DeleteMapping("/{eventoId}/listas/{listaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removerListaDoEvento(
            @PathVariable UUID eventoId,
            @PathVariable UUID listaId) {
        service.removerListaDoEvento(eventoId, listaId);
    }

}

