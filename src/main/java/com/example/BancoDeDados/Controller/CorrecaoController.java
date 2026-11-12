package com.example.BancoDeDados.Controller;

import com.example.BancoDeDados.Security.CallerContext;
import com.example.BancoDeDados.Services.CorrecaoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

/** FE-201: fila de correção das respostas abertas (docs/professor-api.md do front). */
@RestController
@RequestMapping("/correcao")
public class CorrecaoController {

    public record CorrecaoRequest(BigDecimal nota, String feedback) {}

    @Autowired
    private CorrecaoService service;
    @Autowired
    private com.example.BancoDeDados.Services.PermissaoService permissoes;

    @GetMapping("/pendentes")
    public CorrecaoService.Fila pendentes(@RequestParam(required = false) UUID listaId,
                                          @RequestParam(required = false) UUID disciplinaId,
                                          @RequestParam(required = false) String busca) {
        CallerContext.exigirStaff();
        permissoes.exigir(com.example.BancoDeDados.Model.GestaoInstitucional.CORRIGIR);
        return service.listar(CallerContext.professorIdOuNull(), listaId, disciplinaId, busca);
    }

    @GetMapping("/respostas/{id}")
    public CorrecaoService.Detalhe detalhe(@PathVariable Long id) {
        CallerContext.exigirStaff();
        permissoes.exigir(com.example.BancoDeDados.Model.GestaoInstitucional.CORRIGIR);
        return service.detalhe(id, CallerContext.professorIdOuNull());
    }

    @PutMapping("/respostas/{id}")
    public CorrecaoService.Resultado corrigir(@PathVariable Long id, @RequestBody CorrecaoRequest body) {
        CallerContext.exigirStaff();
        permissoes.exigir(com.example.BancoDeDados.Model.GestaoInstitucional.CORRIGIR);
        return service.corrigir(id, CallerContext.professorIdOuNull(), body.nota(), body.feedback());
    }
}
