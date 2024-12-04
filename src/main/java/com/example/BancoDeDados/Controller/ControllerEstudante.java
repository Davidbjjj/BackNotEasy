package com.example.BancoDeDados.Controller;

import java.lang.Integer;
import java.util.Optional;

import org.apache.catalina.connector.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.repository.support.Repositories;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.ui.Model;

import com.example.BancoDeDados.Model.Estudante;
import com.example.BancoDeDados.Model.Usuario;
import com.example.BancoDeDados.Repositores.EstudanteRepositores;
import com.example.BancoDeDados.ResponseDTO.ELoginRespondeDTO;
import com.example.BancoDeDados.ResponseDTO.EstudanteResponseDTO;
import com.example.BancoDeDados.Security.TokenService;
import com.example.BancoDeDados.Services.ServiceEstudante;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/estudantes")
@RestController
@CrossOrigin(origins = "http://localhost:3000")
public class ControllerEstudante {

    @Autowired
    private ServiceEstudante serviceEstudante;
    @Autowired
    private EstudanteRepositores estudanteRepositores;
    @Autowired
    private TokenService tokenService;

    @PostMapping("/cadastro")
    public ResponseEntity<?> cadastrar(@RequestBody EstudanteResponseDTO estudanteResponseDTO) {
        Estudante estudante = new Estudante(estudanteResponseDTO);
        try {
            serviceEstudante.criar(estudante);

            String token = tokenService.gerarTokenEstudante(estudante);
            return ResponseEntity.ok(new ELoginRespondeDTO(token, estudante.getNome()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro ao cadastrar o estudante." + e.getMessage());
        }
    }

    @GetMapping("/listar")
    public String listar(Model model, Estudante estudante) {

        return model.addAttribute("estudante", this.serviceEstudante.listaEstudantes(estudante)).toString();
    }

    @DeleteMapping("/deletar/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Integer id) {

        if (serviceEstudante.deletar(id)) {
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping("/editar/{id}")
    public ResponseEntity<Estudante> editar(@PathVariable Integer id) {
        Optional<Estudante> estudanteOpt = serviceEstudante.editar(id);
        return estudanteOpt.map(estudante -> new ResponseEntity<>(estudante, HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

}
