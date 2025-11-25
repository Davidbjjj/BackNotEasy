package com.example.BancoDeDados.Controller;

import com.example.BancoDeDados.Model.Disciplina;
import com.example.BancoDeDados.Model.Estudante;
import com.example.BancoDeDados.Repositores.DisciplinaRepository;
import com.example.BancoDeDados.Repositores.EstudanteRepositores;
import com.example.BancoDeDados.ResponseDTO.DisciplinaRequestDTO;
import com.example.BancoDeDados.ResponseDTO.DisciplinaResponseDTO;
import com.example.BancoDeDados.ResponseDTO.DisciplinaResponseCountDTO;
import com.example.BancoDeDados.ResponseDTO.AlunoMediaDTO;
import com.example.BancoDeDados.ResponseDTO.ListaMediaDTO;
import com.example.BancoDeDados.ResponseDTO.AtividadeConcluidaDTO;
import com.example.BancoDeDados.ResponseDTO.EstudanteSimplesDTO;
import com.example.BancoDeDados.ResponseDTO.ListaAtividadeDTO;
import com.example.BancoDeDados.Services.DisciplinaService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/disciplinas")
public class DisciplinaController {

    private final DisciplinaService disciplinaService;
    private final DisciplinaRepository disciplinaRepository;
    private final EstudanteRepositores estudanteRepository;

    public DisciplinaController(DisciplinaService disciplinaService,
                                DisciplinaRepository disciplinaRepository,
                                EstudanteRepositores estudanteRepository) {
        this.disciplinaService = disciplinaService;
        this.disciplinaRepository =disciplinaRepository;
        this.estudanteRepository=estudanteRepository;
    }

    @PostMapping
    public ResponseEntity<DisciplinaResponseDTO> criar(@RequestBody DisciplinaRequestDTO dto) {
        var disciplina = disciplinaService.criar(dto);
        return ResponseEntity.ok(new DisciplinaResponseDTO(
                disciplina.getId(),
                disciplina.getNome(),
                disciplina.getProfessor() != null ? disciplina.getProfessor().getId() : null,
                disciplina.getProfessor().getNome(),
                disciplina.getInstituicao().getNome(),
                disciplina.getEstudantes().stream().map(a -> a.getNome()).collect(Collectors.toList())
        ));
    }

    @GetMapping
    public ResponseEntity<List<DisciplinaResponseDTO>> listar() {
        return ResponseEntity.ok(disciplinaService.listar());
    }

    @GetMapping("/{id}")
    public ResponseEntity<DisciplinaResponseDTO> buscarPorId(@PathVariable UUID id) {
        return disciplinaService.buscarPorId(id)
                .map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/instituicao/{instituicaoId}")
    public ResponseEntity<List<DisciplinaResponseCountDTO>> buscarPorEscola(@PathVariable UUID instituicaoId) {
        return ResponseEntity.ok(disciplinaService.buscarPorInstituicao(instituicaoId));
    }

    @GetMapping("/professor/{professorId}")
    public ResponseEntity<List<DisciplinaResponseDTO>> buscarPorProfessor(@PathVariable UUID professorId) {
        return ResponseEntity.ok(disciplinaService.buscarPorProfessor(professorId));
    }

    @GetMapping("/estudante/{estudanteId}")
    public ResponseEntity<List<DisciplinaResponseDTO>> buscarPorEstudante(@PathVariable UUID estudanteId) {
        return ResponseEntity.ok(disciplinaService.buscarPorEstudante(estudanteId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable UUID id) {
        disciplinaService.deletar(id);
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/{disciplinaId}/estudantes/{estudanteId}")
    public ResponseEntity<DisciplinaResponseDTO> adicionarEstudante(
            @PathVariable UUID disciplinaId,
            @PathVariable UUID estudanteId) {
        try {
            DisciplinaResponseDTO disciplinaAtualizada = disciplinaService.adicionarEstudantee(disciplinaId, estudanteId);
            return ResponseEntity.ok(disciplinaAtualizada);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
    @GetMapping("/debug/{disciplinaId}/estudantes/{estudanteId}")
    public ResponseEntity<?> verificarIds(
            @PathVariable UUID disciplinaId,
            @PathVariable UUID estudanteId) {

        Map<String, Object> response = new HashMap<>();

        // Verificar disciplina
        boolean disciplinaExiste = disciplinaRepository.existsById(disciplinaId);
        response.put("disciplinaExiste", disciplinaExiste);

        if (disciplinaExiste) {
            Disciplina disciplina = disciplinaRepository.findById(disciplinaId).get();
            response.put("disciplinaNome", disciplina.getNome());
        }

        // Verificar estudante
        boolean estudanteExiste = estudanteRepository.existsById(estudanteId);
        response.put("estudanteExiste", estudanteExiste);

        if (estudanteExiste) {
            Estudante estudante = estudanteRepository.findById(estudanteId).get();
            response.put("estudanteNome", estudante.getNome());
        }

        return ResponseEntity.ok(response);
    }


    // NOVO ENDPOINT 2: Associar lista à disciplina (e automaticamente adicionar estudantes à lista)
    @PostMapping("/{disciplinaId}/listas/{listaId}")
    public ResponseEntity<DisciplinaResponseDTO> associarLista(
            @PathVariable UUID disciplinaId,
            @PathVariable UUID listaId) {
        try {
            DisciplinaResponseDTO disciplinaAtualizada = disciplinaService.associarLista(disciplinaId, listaId);
            return ResponseEntity.ok(disciplinaAtualizada);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // NOVO ENDPOINT: Remover estudante da disciplina
    @DeleteMapping("/{disciplinaId}/estudantes/{estudanteId}")
    public ResponseEntity<DisciplinaResponseDTO> removerEstudante(
            @PathVariable UUID disciplinaId,
            @PathVariable UUID estudanteId) {
        try {
            DisciplinaResponseDTO disciplinaAtualizada = disciplinaService.removerEstudante(disciplinaId, estudanteId);
            return ResponseEntity.ok(disciplinaAtualizada);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // NOVO ENDPOINT: Listar estudantes da disciplina
    @GetMapping("/{disciplinaId}/estudantes")
    public ResponseEntity<List<EstudanteSimplesDTO>> listarEstudantes(@PathVariable UUID disciplinaId) {
        try {
            List<EstudanteSimplesDTO> estudantes = disciplinaService.listarEstudantes(disciplinaId);
            return ResponseEntity.ok(estudantes);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // Analytics endpoints (professor-facing). For now professorId is required as query param to check ownership.
    @GetMapping("/{disciplinaId}/analytics/alunos/medias")
    public ResponseEntity<List<AlunoMediaDTO>> listarAlunoMedias(@PathVariable UUID disciplinaId,
                                                                 @RequestParam UUID solicitanteId) {
        try {
            List<AlunoMediaDTO> result = disciplinaService.getAlunoMedias(disciplinaId, solicitanteId);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("Acesso negado")) return ResponseEntity.status(403).build();
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{disciplinaId}/analytics/listas/menor-media")
    public ResponseEntity<List<ListaMediaDTO>> listarListasMenorMedia(@PathVariable UUID disciplinaId,
                                                                      @RequestParam UUID solicitanteId) {
        try {
            List<ListaMediaDTO> result = disciplinaService.getListasMenoresMedias(disciplinaId, solicitanteId);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("Acesso negado")) return ResponseEntity.status(403).build();
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{disciplinaId}/analytics/atividades-concluidas")
    public ResponseEntity<List<AtividadeConcluidaDTO>> listarAtividadesConcluidas(@PathVariable UUID disciplinaId,
                                                                                 @RequestParam UUID solicitanteId) {
        try {
            List<AtividadeConcluidaDTO> result = disciplinaService.getAtividadesConcluidas(disciplinaId, solicitanteId);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("Acesso negado")) return ResponseEntity.status(403).build();
            return ResponseEntity.notFound().build();
        }
    }

    // Listar todas as atividades (listas) de uma disciplina
    @GetMapping("/{disciplinaId}/atividades")
    public ResponseEntity<List<ListaAtividadeDTO>> listarAtividades(@PathVariable UUID disciplinaId) {
        try {
            List<ListaAtividadeDTO> atividades = disciplinaService.listarAtividades(disciplinaId);
            return ResponseEntity.ok(atividades);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
