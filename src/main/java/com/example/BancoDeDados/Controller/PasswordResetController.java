package com.example.BancoDeDados.Controller;

import com.example.BancoDeDados.ResponseDTO.*;
import com.example.BancoDeDados.Services.PasswordResetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class PasswordResetController {

    @Autowired
    private PasswordResetService passwordResetService;

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponseDTO> forgotPassword(@RequestBody ForgotPasswordRequestDTO request) {
        boolean result = passwordResetService.requestPasswordReset(request.email());

        ApiResponseDTO response = new ApiResponseDTO(
                "Se o email estiver cadastrado, você receberá um token de recuperação.",
                true
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/validate-token")
    public ResponseEntity<ValidateTokenResponseDTO> validateToken(@RequestBody ValidateTokenRequestDTO request) {
        ValidateTokenResponseDTO response = passwordResetService.validateToken(request.token());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponseDTO> resetPassword(@RequestBody ResetPasswordRequestDTO request) {
        boolean result = passwordResetService.resetPassword(request.token(), request.newPassword());

        if (result) {
            ApiResponseDTO response = new ApiResponseDTO(
                    "Senha alterada com sucesso!",
                    true
            );
            return ResponseEntity.ok(response);
        } else {
            ApiResponseDTO response = new ApiResponseDTO(
                    "Erro ao alterar senha. Token pode ter expirado.",
                    false
            );
            return ResponseEntity.badRequest().body(response);
        }
    }
}