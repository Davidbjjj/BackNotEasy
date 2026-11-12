package com.example.BancoDeDados.Exceptions;


import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(EmailJaCadastradoException.class)
  public ResponseEntity<String> handleEmailDuplicado(EmailJaCadastradoException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
  }

  /** Regras de negócio violadas (ex.: lista fora da janela de resposta) voltam como 400 com a mensagem. */
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<String> handleArgumentoInvalido(IllegalArgumentException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
  }
}
