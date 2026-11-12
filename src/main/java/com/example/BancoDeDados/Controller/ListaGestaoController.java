package com.example.BancoDeDados.Controller;

import com.example.BancoDeDados.ResponseDTO.ListaResponseDTO;
import com.example.BancoDeDados.Security.CallerContext;
import com.example.BancoDeDados.Services.ListaGestaoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** FE-203, FE-205 e FE-206: configuração, publicação, duplicação, reabertura e reforço de listas. */
@RestController
public class ListaGestaoController {

    @Autowired
    private ListaGestaoService service;

    @GetMapping("/listas/{listaId}/configuracao")
    public ListaResponseDTO obter(@PathVariable UUID listaId) {
        CallerContext.exigirStaff();
        return service.obter(listaId, CallerContext.professorIdOuNull());
    }

    @PutMapping("/listas/{listaId}/configuracao")
    public ListaResponseDTO configurar(@PathVariable UUID listaId, @RequestBody ListaGestaoService.Configuracao body) {
        CallerContext.exigirStaff();
        return service.configurar(listaId, CallerContext.professorIdOuNull(), body);
    }

    @PostMapping("/listas/{listaId}/publicar")
    public ListaResponseDTO publicar(@PathVariable UUID listaId) {
        CallerContext.exigirStaff();
        return service.publicar(listaId, CallerContext.professorIdOuNull());
    }

    @PostMapping("/listas/{listaId}/duplicar")
    public ListaResponseDTO duplicar(@PathVariable UUID listaId, @RequestBody ListaGestaoService.DuplicarRequest body) {
        CallerContext.exigirStaff();
        return service.duplicar(listaId, CallerContext.professorIdOuNull(), body);
    }

    @PostMapping("/listas/{listaId}/reabrir")
    public ListaGestaoService.ReabrirResultado reabrir(@PathVariable UUID listaId,
                                                       @RequestBody ListaGestaoService.ReabrirRequest body) {
        CallerContext.exigirStaff();
        return service.reabrir(listaId, CallerContext.professorIdOuNull(), body);
    }

    @PostMapping("/listas/reforco")
    public ListaGestaoService.ReforcoResultado reforco(@RequestBody ListaGestaoService.ReforcoRequest body) {
        CallerContext.exigirStaff();
        return service.criarReforco(CallerContext.professorIdOuNull(), body);
    }

    @GetMapping("/disciplinas/{disciplinaId}/assuntos-fracos")
    public List<ListaGestaoService.AssuntoFraco> assuntosFracos(@PathVariable UUID disciplinaId,
                                                                @RequestParam(defaultValue = "5") int limite) {
        CallerContext.exigirStaff();
        return service.assuntosFracos(disciplinaId, CallerContext.professorIdOuNull(), limite);
    }
}
