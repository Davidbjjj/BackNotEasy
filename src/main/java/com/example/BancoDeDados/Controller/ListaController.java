package com.example.BancoDeDados.Controller;

import com.example.BancoDeDados.Model.Estudante;
import com.example.BancoDeDados.Model.Lista;
import com.example.BancoDeDados.Model.Questao;
import com.example.BancoDeDados.Repositores.ListaRepository;
import com.example.BancoDeDados.Repositores.QuestaoRepositores;
import com.example.BancoDeDados.Repositores.projections.QuestaoListProjection;
import com.example.BancoDeDados.ResponseDTO.*;
import com.example.BancoDeDados.Services.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.*;

@RestController
@RequestMapping("/listas")
public class ListaController {

    private final ListaService listaService;

    @Autowired
    private ListaRepository listaRepository;

    @Autowired
    private QuestaoRepositores questaoRepository;

    @Autowired
    private QuestaoService questaoService;

    @Autowired
    private TratarRespostaIAService tratarRespostaIAService;

    @Autowired
    private EventoService eventoService;

    @Autowired
    private NotaListaService notaListaService;

    @Autowired
    private QuestaoOtimizadoService questaoOtimizadoService;

    public ListaController(ListaService listaService) {
        this.listaService = listaService;
    }

    @GetMapping("/{listaId}/notas")
    public ResponseEntity<List<AlunoNotaDTO>> getNotasByListaId(@PathVariable UUID listaId) {
        try {
            List<AlunoNotaDTO> notas = listaService.getNotasByListaId(listaId);
            return ResponseEntity.ok(notas);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping
    public List<ListaResponseDTO> listarTodasListas() {
        return listaService.buscarTodasListas();
    }

    // Adicione este método no ListaController.java

    // No ListaController.java - Método corrigido
    @GetMapping("/estudante/{estudanteId}/questoes")
    public ResponseEntity<List<QuestaoResponseDTO>> buscarQuestoesPorEstudante(@PathVariable UUID estudanteId) {
        try {
            List<QuestaoResponseDTO> questoes = listaService.buscarQuestoesPorEstudante(estudanteId);
            return ResponseEntity.ok(questoes);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(null);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/salvar-questoes-docs/{listaId}")
    public ListaAddResponseDTO salvarQuestoesDoPdf(@PathVariable UUID listaId) {
        List<Questao> questoesProcessadas = tratarRespostaIAService.processarRespostaIA();

        List<Questao> questoesSalvas = questaoService.salvarQuestoes(questoesProcessadas);

        List<Integer> questaoIds = questoesSalvas.stream()
                .map(Questao::getId)
                .collect(Collectors.toList());

        listaService.adicionarQuestoesEmLote(listaId, questaoIds);

        return new ListaAddResponseDTO(listaId, questoesSalvas);
    }

    @PostMapping("/{listaId}/questoes")
    public ListaResponseDTO adicionarQuestao(@PathVariable UUID listaId,
                                             @RequestBody QuestaoRequestDTO questaoRequest) {

        System.out.println("Ver se a questão que está vindo daqui vem vazia: " + questaoRequest.getEnunciado());

        Questao novaQuestao = questaoService.criarQuestao(
                questaoRequest.getCabecalho(),
                questaoRequest.getEnunciado(),
                questaoRequest.getAlternativas(),
                questaoRequest.getGabarito()
        );

        ListaResponseDTO listaResponseDTO = listaService.adicionarQuestao(listaId, novaQuestao.getId());
        return listaResponseDTO;
    }
    @PostMapping("/{listaId}/questoes/{questaoId}")
    public ListaResponseDTO adicionarQuestaoExistente(@PathVariable UUID listaId, @PathVariable Integer questaoId) {
        return listaService.adicionarQuestaoExistente(listaId, questaoId);
    }
    @GetMapping("/{listaId}/questoes")
    @Transactional(readOnly = true)
    public List<com.example.BancoDeDados.dto.QuestaoComAlternativasDTO> listarQuestoes(@PathVariable UUID listaId) {
        System.out.println("DEBUG - Buscando questões com imagens para lista ID: " + listaId);

        // Usar serviço otimizado que já retorna questões COM imagens
        List<com.example.BancoDeDados.dto.QuestaoComAlternativasDTO> questoesDTO =
            questaoOtimizadoService.buscarQuestoesComAlternativasPorLista(listaId);

        System.out.println("DEBUG - Total de questões retornadas: " + questoesDTO.size());
        questoesDTO.forEach(q -> {
            System.out.println("DEBUG - Questão " + q.getId() + " tem " +
                (q.getImagens() != null ? q.getImagens().size() : 0) + " imagens");
        });


        System.out.println("DEBUG - Total de DTOs criados: " + questoesDTO.size());
        return questoesDTO;
    }

    @GetMapping("/professor/{professorId}")
    public List<ListaResponseDTO> buscarListasPorProfessor(@PathVariable UUID professorId) {
        return listaService.buscarListasPorProfessor(professorId);
    }

    @PostMapping
    public ListaResponseDTO criarLista(@RequestParam String titulo, @RequestParam UUID professorId) {
        return listaService.criarLista(titulo, professorId);
    }

    @PutMapping("/{listaId}")
    public ListaResponseDTO editarLista(@PathVariable UUID listaId, @RequestParam String novoTitulo) {
        return listaService.editarLista(listaId, novoTitulo);
    }

    @DeleteMapping("/{listaId}")
    public void excluirLista(@PathVariable UUID listaId) {
        listaService.excluirLista(listaId);
    }

    // Adicionar um estudante à lista
    @PostMapping("/{listaId}/estudantes")
    public ListaResponseDTO adicionarEstudante(@PathVariable UUID listaId, @RequestParam UUID estudanteId) {
        ListaResponseDTO listaAtualizada = listaService.adicionarEstudante(listaId, estudanteId);
        return listaAtualizada;
    }

    // Remover um estudante da lista
    @DeleteMapping("/{listaId}/estudantes/{estudanteId}")
    public ListaResponseDTO removerEstudante(@PathVariable UUID listaId, @PathVariable UUID estudanteId) {
        ListaResponseDTO listaAtualizada = listaService.removerEstudante(listaId, estudanteId);
        return listaAtualizada;
    }

    // Listar estudantes associados a uma lista
    @GetMapping("/{listaId}/estudantes")
    public List<Estudante> listarEstudantes(@PathVariable UUID listaId) {
        Lista lista = listaRepository.findById(listaId)
                .orElseThrow(() -> new RuntimeException("Lista não encontrada"));
        return lista.getEstudantes();
    }
    @PostMapping("/eventos/{eventoId}/listas/{listaId}/sincronizar-notas")
    public ResponseEntity<?> sincronizarNotas(
            @PathVariable UUID eventoId,
            @PathVariable UUID listaId) {
        try {
            eventoService.sincronizarNotasListaEvento(eventoId, listaId);
            return ResponseEntity.ok("Notas sincronizadas com sucesso");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    @PostMapping("/lista/{listaId}/estudante/{estudanteId}/calcular")
    public ResponseEntity<?> calcularNota(
            @PathVariable UUID listaId,
            @PathVariable UUID estudanteId) {
        try {
            var nota = notaListaService.calcularESalvarNota(listaId, estudanteId);
            return ResponseEntity.ok(nota);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/lista/{listaId}/calcular-todos")
    public ResponseEntity<?> calcularNotasParaTodos(@PathVariable UUID listaId) {
        try {
            notaListaService.calcularNotasParaTodosEstudantes(listaId);
            return ResponseEntity.ok("Notas calculadas para todos os estudantes");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/lista/{listaId}/estudante/{estudanteId}")
    public ResponseEntity<?> buscarNotaEstudante(
            @PathVariable UUID listaId,
            @PathVariable UUID estudanteId) {
        try {
            var nota = notaListaService.buscarNotaEstudante(listaId, estudanteId);
            return ResponseEntity.ok(nota);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/lista/{listaId}")
    public ResponseEntity<?> buscarNotasPorLista(@PathVariable UUID listaId) {
        try {
            var notas = notaListaService.buscarNotasPorLista(listaId);
            return ResponseEntity.ok(notas);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
    @GetMapping("/estudante/{estudanteId}")
    public List<ListaResponseDTO> buscarListasPorEstudante(@PathVariable UUID estudanteId) {
        return listaService.buscarListasPorEstudante(estudanteId);
    }
    // NOVO ENDPOINT: Criar lista associada a uma disciplina
    @PostMapping("/disciplina/{disciplinaId}")
    public ResponseEntity<ListaResponseDTO> criarListaComDisciplina(
            @RequestParam String titulo,
            @RequestParam UUID professorId,
            @PathVariable UUID disciplinaId) {
        try {
            ListaResponseDTO listaCriada = listaService.criarListaComDisciplina(titulo, professorId, disciplinaId);
            return ResponseEntity.ok(listaCriada);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(null);
        }
    }

    // NOVO ENDPOINT: Associar lista existente a uma disciplina
    @PostMapping("/{listaId}/disciplina/{disciplinaId}")
    public ResponseEntity<ListaResponseDTO> associarListaADisciplina(
            @PathVariable UUID listaId,
            @PathVariable UUID disciplinaId) {
        try {
            ListaResponseDTO listaAtualizada = listaService.associarListaExistenteADisciplina(listaId, disciplinaId);
            return ResponseEntity.ok(listaAtualizada);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(null);
        }
    }

    // NOVO ENDPOINT: Buscar listas por disciplina
    @GetMapping("/disciplina/{disciplinaId}")
    public ResponseEntity<List<ListaResponseDTO>> buscarListasPorDisciplina(@PathVariable UUID disciplinaId) {
        try {
            List<ListaResponseDTO> listas = listaService.buscarListasPorDisciplina(disciplinaId);
            return ResponseEntity.ok(listas);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // NOVO ENDPOINT: Buscar disciplinas do professor
    @GetMapping("/professor/{professorId}/disciplinas")
    public ResponseEntity<List<DisciplinaProfessorResponseDTO>> buscarDisciplinasPorProfessor(@PathVariable UUID professorId) {
        try {
            List<DisciplinaProfessorResponseDTO> disciplinas = listaService.buscarDisciplinasPorProfessor(professorId);
            return ResponseEntity.ok(disciplinas);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // Novo: Atualizar questão da lista (professor)
    @PutMapping("/{listaId}/questoes/{questaoId}")
    public ResponseEntity<QuestaoResponseDTO> atualizarQuestaoDaLista(@PathVariable UUID listaId,
                                                                      @PathVariable Integer questaoId,
                                                                      @RequestBody QuestaoRequestDTO req) {
        try {
            var dto = questaoService.atualizarQuestaoDaLista(listaId, questaoId, req);
            return ResponseEntity.ok(dto);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // Novo: Deletar questão da lista (professor)
    @DeleteMapping("/{listaId}/questoes/{questaoId}")
    public ResponseEntity<Void> deletarQuestaoDaLista(@PathVariable UUID listaId, @PathVariable Integer questaoId) {
        try {
            questaoService.deletarQuestaoDaLista(listaId, questaoId);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
