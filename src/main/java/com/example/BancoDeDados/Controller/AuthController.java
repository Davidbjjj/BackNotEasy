package com.example.BancoDeDados.Controller;

import com.example.BancoDeDados.Model.Account;
import com.example.BancoDeDados.Repositores.AccountRepository;
import com.example.BancoDeDados.Security.TokenService;
import com.example.BancoDeDados.ResponseDTO.LoginRequestDTO;
import com.example.BancoDeDados.ResponseDTO.LoginResponseDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final TokenService tokenService;
    private final AccountRepository accountRepository;

    @Autowired // A injeção de PasswordEncoder não é mais necessária aqui se a validação for feita pelo AuthenticationManager
    private PasswordEncoder passwordEncoder;

    public AuthController(AuthenticationManager authenticationManager,
                          TokenService tokenService,
                          AccountRepository accountRepository) {
        this.authenticationManager = authenticationManager;
        this.tokenService = tokenService;
        this.accountRepository = accountRepository;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequestDTO dto) {
        try {
            // 1. Cria o objeto de autenticação com email e senha
            var usernamePassword = new UsernamePasswordAuthenticationToken(dto.email(), dto.senha());
            
            // 2. O AuthenticationManager valida as credenciais. Se estiverem erradas, lança uma exceção.
            var auth = this.authenticationManager.authenticate(usernamePassword);
            
            // 3. Se a autenticação for bem-sucedida, o principal será o seu objeto Account
            var account = (Account) auth.getPrincipal();
            
            // 4. Gera o token com base no usuário autenticado
            String token = tokenService.gerarToken(account);

            return ResponseEntity.ok(new LoginResponseDTO(account.getId(), account.getEmail(), token));
        
        } catch (org.springframework.security.core.AuthenticationException e) {
            // Captura exceções de autenticação (ex: usuário não encontrado, senha errada)
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Credenciais inválidas.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro no processo de login: " + e.getMessage());
        }
    }
}