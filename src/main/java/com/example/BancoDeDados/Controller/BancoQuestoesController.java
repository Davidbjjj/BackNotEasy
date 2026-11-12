package com.example.BancoDeDados.Controller;

import com.example.BancoDeDados.Security.CallerContext;
import com.example.BancoDeDados.Services.BancoQuestoesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** FE-202: banco de questões (docs/professor-api.md do front). */
@RestController
@RequestMapping("/banco-questoes")
public class BancoQuestoesController {

    public record AdicionarRequest(UUID listaId, List<Integer> questaoIds) {}

    @Autowired
    private BancoQuestoesService service;
    @Autowired
    private com.example.BancoDeDados.Services.PermissaoService permissoes;

    @GetMapping
    public BancoQuestoesService.Pagina buscar(@RequestParam(required = false) String q,
                                              @RequestParam(required = false) String assunto,
                                              @RequestParam(required = false) String dificuldade,
                                              @RequestParam(required = false) String tag,
                                              @RequestParam(required = false) UUID disciplinaId,
                                              @RequestParam(required = false) String tipo,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "10") int size) {
        CallerContext.exigirStaff();
        permissoes.exigir(com.example.BancoDeDados.Model.GestaoInstitucional.BANCO_QUESTOES);
        return service.buscar(CallerContext.professorIdOuNull(), q, assunto, dificuldade, tag, disciplinaId, tipo, page, size);
    }

    @GetMapping("/filtros")
    public BancoQuestoesService.Filtros filtros() {
        CallerContext.exigirStaff();
        permissoes.exigir(com.example.BancoDeDados.Model.GestaoInstitucional.BANCO_QUESTOES);
        return service.filtros(CallerContext.professorIdOuNull());
    }

    @GetMapping("/{id}")
    public BancoQuestoesService.Item detalhe(@PathVariable Integer id) {
        CallerContext.exigirStaff();
        permissoes.exigir(com.example.BancoDeDados.Model.GestaoInstitucional.BANCO_QUESTOES);
        return service.detalhe(id, CallerContext.professorIdOuNull());
    }

    /** Corpo parcial: {@code tags}, {@code dificuldade} e/ou {@code assunto}. */
    @PatchMapping("/{id}")
    public BancoQuestoesService.Item classificar(@PathVariable Integer id, @RequestBody Map<String, Object> corpo) {
        CallerContext.exigirStaff();
        permissoes.exigir(com.example.BancoDeDados.Model.GestaoInstitucional.BANCO_QUESTOES);
        return service.classificar(id, CallerContext.professorIdOuNull(), corpo);
    }

    @PostMapping("/adicionar-a-lista")
    public BancoQuestoesService.ResultadoLote adicionar(@RequestBody AdicionarRequest body) {
        CallerContext.exigirStaff();
        permissoes.exigir(com.example.BancoDeDados.Model.GestaoInstitucional.BANCO_QUESTOES);
        return service.adicionarALista(CallerContext.professorIdOuNull(), body.listaId(), body.questaoIds());
    }
}
