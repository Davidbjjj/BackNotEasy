package com.example.BancoDeDados.Controller;

import com.example.BancoDeDados.Exceptions.EmailJaCadastradoException;
import com.example.BancoDeDados.Model.Account;
import com.example.BancoDeDados.Model.Instituicao;
import com.example.BancoDeDados.Model.Role;
import com.example.BancoDeDados.Repositores.AccountRepository;
import com.example.BancoDeDados.Repositores.InstituicaoRepository;
import com.example.BancoDeDados.ResponseDTO.*;
import com.example.BancoDeDados.Security.TokenService;
import com.example.BancoDeDados.Services.EmailService;
import com.example.BancoDeDados.Services.InstituicaoService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/instituicao")
public class InstituicaoController {

    private final InstituicaoService instituicaoService;
    private final AccountRepository accountRepository;
    private final TokenService tokenService;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final InstituicaoRepository instituicaoRepository;

    public InstituicaoController(InstituicaoService instituicaoService,
                                 AccountRepository accountRepository,
                                 TokenService tokenService,
                                 EmailService emailService,
                                 PasswordEncoder passwordEncoder,
                                 InstituicaoRepository instituicaoRepository) {
        this.instituicaoService = instituicaoService;
        this.accountRepository = accountRepository;
        this.tokenService = tokenService;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.instituicaoRepository=instituicaoRepository;
    }

    @PostMapping("/registrar")
    public ResponseEntity<?> registrar(@RequestBody @Valid InstituicaoRequest instituicaoRequest) {
        try {
            Instituicao instituicao=new Instituicao(instituicaoRequest);
            instituicao.setSenha(passwordEncoder.encode(instituicaoRequest.getSenha()));

            instituicao = instituicaoRepository.save(instituicao);

            Account account = new Account();
            account.setEmail(instituicao.getEmail());
            account.setSenha(instituicao.getSenha());
            account.setRole(Role.INSTITUICAO);
            account.setInstituicaoProfile(instituicao);
            accountRepository.save(account);

            String token = tokenService.gerarToken(account);


            String assunto = "Confirmação de cadastro";
            String mensagem = String.format(
                    "Olá %s, obrigado por se cadastrar no nosso site!",
                    instituicao.getNome()
            );
            emailService.enviarEmail(instituicao.getEmail(), assunto, mensagem);

            return ResponseEntity.ok(new EscLoginResponseDTO(token, instituicao.getNome()));

        } catch (EmailJaCadastradoException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro ao cadastrar a instituição: " + e.getMessage());
        }
    }


    @CrossOrigin(originPatterns = "*", allowedHeaders = "*")
    @GetMapping("/listar")
    @ResponseBody
    public ResponseEntity<List<Instituicao>> listar() {
        List<Instituicao> instituicaos = instituicaoService.listar();
        return ResponseEntity.ok(instituicaos);
    }

    @CrossOrigin(originPatterns = "*", allowedHeaders = "*")
    @GetMapping("/editar/{id}")
    public ResponseEntity<Instituicao> editar(@PathVariable UUID id) {
        Optional<Instituicao> instituicaoOpt = instituicaoService.editar(id);
        return instituicaoOpt.map(instituicao -> new ResponseEntity<>(instituicao, HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @CrossOrigin(originPatterns = "*", allowedHeaders = "*")
    @DeleteMapping("/deletar/{id}")
    public ResponseEntity<Void> deletar(@PathVariable UUID id) {
        if (instituicaoService.deletar(id)) {
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
    }@PostMapping("/{instituicaoId}/emails-permitidos")
    public ResponseEntity<?> adicionarEmailsPermitidos(
            @PathVariable UUID instituicaoId,
            @RequestBody EmailsPermitidosRequest request) {

        try {
            // Buscar a instituição pelo ID
            Instituicao instituicao = instituicaoRepository.findById(instituicaoId)
                    .orElseThrow(() -> new IllegalArgumentException("Instituição não encontrada"));

            // Validar se a lista de emails não é nula ou vazia
            if (request.getEmails() == null || request.getEmails().isEmpty()) {
                return ResponseEntity.badRequest().body("A lista de emails não pode estar vazia");
            }

            // Validar formato dos emails
            for (String email : request.getEmails()) {
                if (!isValidEmail(email)) {
                    return ResponseEntity.badRequest().body("Email inválido: " + email);
                }
            }

            // Adicionar os novos emails à lista existente
            List<String> emailsExistentes = instituicao.getEmailsPermitidos();
            emailsExistentes.addAll(request.getEmails());

            // Remover duplicatas se necessário
            List<String> emailsUnicos = emailsExistentes.stream()
                    .distinct()
                    .collect(Collectors.toList());

            instituicao.setEmailsPermitidos(emailsUnicos);

            // Salvar a instituição atualizada
            instituicaoRepository.save(instituicao);

            return ResponseEntity.ok("Emails permitidos adicionados com sucesso");

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro ao adicionar emails permitidos: " + e.getMessage());
        }
    }

    // Método auxiliar para validar email
    private boolean isValidEmail(String email) {
        String emailRegex = "^[A-Za-z0-9+_.-]+@(.+)$";
        return email != null && email.matches(emailRegex);
    }@DeleteMapping("/{instituicaoId}/emails-permitidos")
    public ResponseEntity<?> removerEmailPermitido(
            @PathVariable UUID instituicaoId,
            @RequestParam String email) {

        try {
            Instituicao instituicao = instituicaoRepository.findById(instituicaoId)
                    .orElseThrow(() -> new IllegalArgumentException("Instituição não encontrada"));

            List<String> emailsPermitidos = instituicao.getEmailsPermitidos();

            if (emailsPermitidos.remove(email)) {
                instituicao.setEmailsPermitidos(emailsPermitidos);
                instituicaoRepository.save(instituicao);
                return ResponseEntity.ok("Email removido com sucesso");
            } else {
                return ResponseEntity.badRequest().body("Email não encontrado na lista de permitidos");
            }

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro ao remover email permitido: " + e.getMessage());
        }
    }
    @GetMapping("/{instituicaoId}/emails-permitidos")
    public ResponseEntity<?> listarEmailsPermitidos(@PathVariable UUID instituicaoId) {
        try {
            Instituicao instituicao = instituicaoRepository.findById(instituicaoId)
                    .orElseThrow(() -> new IllegalArgumentException("Instituição não encontrada"));

            return ResponseEntity.ok(instituicao.getEmailsPermitidos());

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro ao buscar emails permitidos: " + e.getMessage());
        }
    }


}
