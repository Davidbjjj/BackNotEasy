package com.example.BancoDeDados.Controller;

import com.example.BancoDeDados.Model.Estudante;
import com.example.BancoDeDados.Model.Lista;
import com.example.BancoDeDados.Model.Questao;
import com.example.BancoDeDados.Repositores.ListaRepository;
import com.example.BancoDeDados.Repositores.QuestaoRepositores;
import com.example.BancoDeDados.ResponseDTO.ListaAddResponseDTO;
import com.example.BancoDeDados.ResponseDTO.ListaResponseDTO;
import com.example.BancoDeDados.ResponseDTO.QuestaoRequestDTO;
import com.example.BancoDeDados.ResponseDTO.QuestaoResponseDTO;
import com.example.BancoDeDados.Services.ListaService;
import com.example.BancoDeDados.Services.QuestaoService;
import com.example.BancoDeDados.Services.TratarRespostaIAService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

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

    public ListaController(ListaService listaService) {
        this.listaService = listaService;
    }

    @GetMapping
    public List<ListaResponseDTO> listarTodasListas() {
        return listaService.buscarTodasListas();
    }

    @PostMapping("/salvar-questoes-do-pdf/{listaId}")
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
    public List<QuestaoResponseDTO> listarQuestoes(@PathVariable UUID listaId) {
        Lista lista = listaRepository.findById(listaId)
                .orElseThrow(() -> new RuntimeException("Lista não encontrada"));

        List<QuestaoResponseDTO> questoesDTO = lista.getQuestoes().stream()
                .map(questao -> new QuestaoResponseDTO(questao.getId(), questao.getCabecalho(),
                        questao.getEnunciado(), questao.getAlternativas(), questao.getGabarito()))
                .collect(toList());

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
}
