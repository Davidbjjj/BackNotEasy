package com.example.BancoDeDados.Controller;

import com.example.BancoDeDados.Exceptions.EmailJaCadastradoException;
import com.example.BancoDeDados.Model.Account;
import com.example.BancoDeDados.Model.Instituicao;
import com.example.BancoDeDados.Model.Role;
import com.example.BancoDeDados.Repositores.AccountRepository;
import com.example.BancoDeDados.ResponseDTO.EscLoginResponseDTO;
import com.example.BancoDeDados.ResponseDTO.InstituicaoRequest;
import com.example.BancoDeDados.ResponseDTO.InstituicaoResponse;
import com.example.BancoDeDados.ResponseDTO.InstituicaoResponseDTO;
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

@RestController
@RequestMapping("/api/instituicoes")
public class InstituicaoController {

    private final InstituicaoService instituicaoService;
    private final AccountRepository accountRepository;
    private final TokenService tokenService;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    public InstituicaoController(InstituicaoService instituicaoService,
                                 AccountRepository accountRepository,
                                 TokenService tokenService,
                                 EmailService emailService,
                                 PasswordEncoder passwordEncoder) {
        this.instituicaoService = instituicaoService;
        this.accountRepository = accountRepository;
        this.tokenService = tokenService;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/registrar")
    public ResponseEntity<?> registrar(@RequestBody @Valid InstituicaoRequest instituicaoRequest) {
        try {
            Instituicao instituicao=new Instituicao(instituicaoRequest);


            Account account = new Account();
            account.setEmail(instituicao.getEmail());
            account.setSenha(passwordEncoder.encode(instituicaoRequest.getSenha()));
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
    }
}
