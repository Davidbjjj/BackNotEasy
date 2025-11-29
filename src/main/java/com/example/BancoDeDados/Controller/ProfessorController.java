package com.example.BancoDeDados.Controller;

import com.example.BancoDeDados.Model.Account;
import com.example.BancoDeDados.Model.Professor;
import com.example.BancoDeDados.Repositores.AccountRepository;
import com.example.BancoDeDados.Repositores.InstituicaoRepository;
import com.example.BancoDeDados.Repositores.ProfessorRepositores;
import com.example.BancoDeDados.ResponseDTO.PLoginResponseDTO;
import com.example.BancoDeDados.ResponseDTO.ProfessorDTO;
import com.example.BancoDeDados.ResponseDTO.ProfessorResponseDTO;
import com.example.BancoDeDados.Security.TokenService;
import com.example.BancoDeDados.Services.EmailService;
import com.example.BancoDeDados.Services.ProfessorService;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/professor")
public class ProfessorController {

    @Autowired
    private ProfessorService professorService;
    @Autowired
    private ProfessorRepositores professorRepositores;
    @Autowired
    private TokenService tokenService;

    @Autowired
    private AuthenticationManager authenticationManager;

    private final PasswordEncoder passwordEncoder;

    @Autowired
    private EmailService emailService;

    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private InstituicaoRepository instituicaoRepository;

    public ProfessorController(TokenService tokenService, AuthenticationManager authenticationManager,
                               PasswordEncoder passwordEncoder, ProfessorRepositores professorRepositores,InstituicaoRepository instituicaoRepository) {
        this.tokenService = tokenService;
        this.authenticationManager = authenticationManager;
        this.passwordEncoder = passwordEncoder;
        this.professorRepositores = professorRepositores;
        this.instituicaoRepository=instituicaoRepository;
    }

    @PostMapping("/registrar")
    public ResponseEntity<?> registrar(@RequestBody @Valid ProfessorResponseDTO professorDTO) {
        try {
            Professor savedProfessor = professorService.registerProfessor(professorDTO);

            // Gera o token JWT com base no Account (busca o account criado)
            Optional<Account> accountOpt = accountRepository.findByProfessorProfile_Id(savedProfessor.getId());
            if (accountOpt.isEmpty()) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Account não foi criado corretamente");
            }
            Account account = accountOpt.get();
            String token = tokenService.gerarToken(account);

            // Envia e-mail de boas-vindas
            String assunto = "Confirmação de cadastro";
            String mensagem = String.format("Olá %s, obrigado por se cadastrar no nosso site!", savedProfessor.getNome());
            emailService.enviarEmail(savedProfessor.getEmail(), assunto, mensagem);

            // Retorno da resposta
            return ResponseEntity.ok(
                    new PLoginResponseDTO(
                            savedProfessor.getId(),
                            token,
                            savedProfessor.getNome()
                    )
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro ao cadastrar o professor. " + e.getMessage());
        }
    }


    @CrossOrigin(originPatterns = "*", allowedHeaders = "*")
    @GetMapping("/listar")
    public ResponseEntity<List<ProfessorDTO>> listarProfessores() {
        List<ProfessorDTO> professores = professorService.listar();
        return ResponseEntity.ok(professores);
    }

    @CrossOrigin(originPatterns = "*", allowedHeaders = "*")
    @GetMapping("/editar/{id}")
    public ResponseEntity<Professor> editar(@PathVariable UUID id) {
        Optional<Professor> professorOpt = professorService.editar(id);
        return professorOpt.map(professor -> new ResponseEntity<>(professor, HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @CrossOrigin(originPatterns = "*", allowedHeaders = "*")
    @DeleteMapping("/deletar/{id}")
    public ResponseEntity<Void> deletar(@PathVariable UUID id) {
        if (professorService.deletar(id)) {
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        } else {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(@RequestHeader("Authorization") String token) {
        String cleanedToken = token.replace("Bearer ", "");

        tokenService.revokeToken(cleanedToken);

        return ResponseEntity.ok("Logout realizado com sucesso");
    }
}