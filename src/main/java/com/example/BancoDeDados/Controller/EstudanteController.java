package com.example.BancoDeDados.Controller;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.example.BancoDeDados.Model.Account;
import com.example.BancoDeDados.Model.Role;
import com.example.BancoDeDados.Repositores.AccountRepository;
import com.example.BancoDeDados.ResponseDTO.DisciplinaRequestDTO;
import com.example.BancoDeDados.ResponseDTO.EstudanteListagemDTO;
import com.example.BancoDeDados.Services.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.example.BancoDeDados.Model.Estudante;
import com.example.BancoDeDados.Repositores.EstudanteRepositores;
import com.example.BancoDeDados.ResponseDTO.ELoginRespondeDTO;
import com.example.BancoDeDados.ResponseDTO.EstudanteResponseDTO;
import com.example.BancoDeDados.Security.TokenService;
import com.example.BancoDeDados.Services.EstudanteService;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/estudantes")
@RestController
public class EstudanteController {

    @Autowired
    private EstudanteService estudanteService;
    @Autowired
    private EstudanteRepositores estudanteRepositores;
    @Autowired
    private TokenService tokenService;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private AccountRepository accountRepository;

    private final PasswordEncoder passwordEncoder;

    @Autowired
    private EmailService emailService;

    public EstudanteController(TokenService tokenService, AuthenticationManager authenticationManager,
                               PasswordEncoder passwordEncoder, EstudanteRepositores estudanteRepositores) {
        this.tokenService = tokenService;
        this.authenticationManager = authenticationManager;
        this.passwordEncoder = passwordEncoder;
        this.estudanteRepositores = estudanteRepositores;
    }

    @PostMapping("/cadastro")
    public ResponseEntity<?> cadastrar(@RequestBody EstudanteResponseDTO estudanteResponseDTO) {
        try {

            Estudante estudante = new Estudante(estudanteResponseDTO);
            estudante.setSenha(passwordEncoder.encode(estudanteResponseDTO.senha()));
            estudanteService.criar(estudante);


            Account account = new Account();
            account.setEmail(estudante.getEmail());
            account.setSenha(estudante.getSenha());
            account.setRole(Role.ALUNO);
            account.setEstudanteProfile(estudante);
            accountRepository.save(account);

            String token = tokenService.gerarToken(account);


            String assunto = "Confirmação de cadastro";
            String mensagem = String.format("Olá %s, obrigado por se cadastrar no nosso site!", estudante.getNome());
            emailService.enviarEmail(estudante.getEmail(), assunto, mensagem);

            return ResponseEntity.ok(new ELoginRespondeDTO(estudante.getId(), token, estudante.getNome()));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro ao cadastrar o estudante: " + e.getMessage());
        }
    }


    @GetMapping("/listar")
    public ResponseEntity<List<EstudanteListagemDTO>> listar() {
        try {
            List<Estudante> estudantes = estudanteService.listaEstudantes(new Estudante());

            List<EstudanteListagemDTO> estudantesDTO = estudantes.stream()
                    .map(estudante -> new EstudanteListagemDTO(
                            estudante.getId(),
                            estudante.getNome(),
                            estudante.getDataNascimento(),
                            estudante.getInstituicao(),
                            estudante.getEmail(),
                            estudante.getDisciplina().stream()
                                    .map(disciplina -> new DisciplinaRequestDTO(
                                            disciplina.getNome(),
                                            disciplina.getProfessor().getEmail(),
                                            disciplina.getInstituicao().getId()
                                    ))
                                    .toList()
                    ))
                    .toList();

            return ResponseEntity.ok(estudantesDTO);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(null);
        }
    }

    @DeleteMapping("/deletar/{id}")
    public ResponseEntity<Void> deletar(@PathVariable UUID id) {

        if (estudanteService.deletar(id)) {
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @GetMapping("/editar/{id}")
    public ResponseEntity<Estudante> editar(@PathVariable UUID id) {
        Optional<Estudante> estudanteOpt = estudanteService.editar(id);
        return estudanteOpt.map(estudante -> new ResponseEntity<>(estudante, HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(@RequestHeader("Authorization") String token) {
        String cleanedToken = token.replace("Bearer ", "");

        tokenService.revokeToken(cleanedToken);

        return ResponseEntity.ok("Logout realizado com sucesso");
    }

}
