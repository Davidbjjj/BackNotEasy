package com.example.BancoDeDados.Controller;

import com.example.BancoDeDados.Model.Account;
import com.example.BancoDeDados.Model.Escola;
import com.example.BancoDeDados.Model.Role;
import com.example.BancoDeDados.Repositores.AccountRepository;
import com.example.BancoDeDados.Repositores.EscolaRespositores;
import com.example.BancoDeDados.ResponseDTO.EscLoginResponseDTO;
import com.example.BancoDeDados.ResponseDTO.EscolaResponseDTO;
import com.example.BancoDeDados.Security.TokenService;
import com.example.BancoDeDados.Services.EmailService;
import com.example.BancoDeDados.Services.EscolaService;

import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/escola")
public class EscolaController {
    @Autowired
    private EscolaService escolaService;
    @Autowired
    private EscolaRespositores escolaRepositores;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private EmailService emailService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private PasswordEncoder passwordEncoder;



    @CrossOrigin(originPatterns = "*", allowedHeaders = "*")
    @PostMapping("/cadastro")
    public ResponseEntity<?> registrar(@RequestBody @Valid EscolaResponseDTO escolaRegristrarDto) {
        try {
            Escola escola = new Escola(escolaRegristrarDto);

            escola.setSenha(passwordEncoder.encode(escolaRegristrarDto.senha()));
            escolaService.criar(escola);

            Account account = new Account();
            account.setEmail(escola.getEmail());
            account.setSenha(escola.getSenha());
            account.setRole(Role.INSTITUICAO);
            account.setInstituicaoProfile(escola);
            accountRepository.save(account);

            String token = tokenService.gerarToken(account);


            String assunto = "Confirmação de cadastro";
            String mensagem = String
                    .format("Olá " + escola.getNome() + " obrigado por se cadastrar no nosso site! ");
            emailService.enviarEmail(escola.getEmail(), assunto, mensagem);
            return ResponseEntity.ok(new EscLoginResponseDTO(token, escola.getNome()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro ao cadastrar a instituição." + e.getMessage());
        }
    }

    @CrossOrigin(originPatterns = "*", allowedHeaders = "*")
    @GetMapping("/listar")
    @ResponseBody
    public ResponseEntity<List<Escola>> listar() {
        List<Escola> escolas = escolaService.listar();
        return ResponseEntity.ok(escolas);
    }

    @CrossOrigin(originPatterns = "*", allowedHeaders = "*")
    @GetMapping("/editar/{id}")
    public ResponseEntity<Escola> editar(@PathVariable UUID id) {
        Optional<Escola> escolaOpt = escolaService.editar(id);
        return escolaOpt.map(escola -> new ResponseEntity<>(escola, HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @CrossOrigin(originPatterns = "*", allowedHeaders = "*")
    @DeleteMapping("/deletar/{id}")
    public ResponseEntity<Void> deletar(@PathVariable UUID id) {
        if (escolaService.deletar(id)) {
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
