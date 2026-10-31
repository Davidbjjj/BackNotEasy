package com.example.BancoDeDados.Controller;


import com.example.BancoDeDados.Model.Account;
import com.example.BancoDeDados.Model.Pais;
import com.example.BancoDeDados.Model.Role;
import com.example.BancoDeDados.Repositores.AccountRepository;
import com.example.BancoDeDados.Repositores.PaisRepositores;
import com.example.BancoDeDados.ResponseDTO.ELoginRespondeDTO;
import com.example.BancoDeDados.ResponseDTO.PaisResponseDTO;
import com.example.BancoDeDados.Security.TokenService;
import com.example.BancoDeDados.Services.EmailService;
import com.example.BancoDeDados.Services.PaisService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/pais")
public class PaisController {

    @Autowired
    private PaisService paisService;
    @Autowired
    private PaisRepositores paisRepositores;
    @Autowired
    private TokenService tokenService;
    @Autowired
    private EmailService emailService;
    @Autowired
    private AuthenticationManager authenticationManager;
    @Autowired
    private AccountRepository accountRepository;

    private PasswordEncoder passwordEncoder;

    public PaisController(TokenService tokenService, AuthenticationManager authenticationManager,
                            PasswordEncoder passwordEncoder, PaisRepositores paisRepositores) {
        this.tokenService = tokenService;
        this.authenticationManager = authenticationManager;
        this.passwordEncoder = passwordEncoder;
        this.paisRepositores = paisRepositores;
    }

    @CrossOrigin(originPatterns = "*", allowedHeaders = "*")
    @PostMapping("/cadastro")
    public ResponseEntity<?> registrar(@RequestBody @Valid PaisResponseDTO paisDTO) {
        try {
            // Cria o objeto Pais (dados pessoais)
            Pais novoPais = new Pais(paisDTO);
            paisService.createPais(novoPais);

            // Cria a conta (Account) vinculada ao Pai
            Account account = new Account();
            account.setEmail(paisDTO.email());
            account.setSenha(passwordEncoder.encode(paisDTO.senha()));
            account.setRole(Role.PAIS);
            account.setPaisProfile(novoPais);

            accountRepository.save(account);

            // Gera o token JWT a partir da Account
            String token = tokenService.gerarToken(account);

            // Envia o e-mail de boas-vindas
            String assunto = "Confirmação de cadastro";
            String mensagem = String.format("Olá %s, obrigado por se cadastrar no nosso site!", novoPais.getNome());
            emailService.enviarEmail(novoPais.getEmail(), assunto, mensagem);

            return ResponseEntity.ok(new ELoginRespondeDTO(novoPais.getId(), token, novoPais.getNome()));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro ao cadastrar o Pai: " + e.getMessage());
        }
    }


    @CrossOrigin(originPatterns = "*", allowedHeaders = "*")
    @GetMapping("/listar")
    public String listar(Model model, Pais pais) {
        return model.addAttribute("pais", this.paisService.listarPais(pais)).toString();
    }

    @CrossOrigin(originPatterns = "*", allowedHeaders = "*")
    @GetMapping("/editar/{id}")
    public ResponseEntity<Pais> editar(@PathVariable Integer id) {
        Optional<Pais> paisOpt = paisService.editar(id);
        return paisOpt.map(pais -> new ResponseEntity<>(pais, HttpStatus.OK))
                .orElse(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }
    @CrossOrigin(originPatterns = "*", allowedHeaders = "*")
    @DeleteMapping("/deletar/{id}")
    public ResponseEntity<Void> deletar(@PathVariable Integer id) {
        if (paisService.deletar(id)) {
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
