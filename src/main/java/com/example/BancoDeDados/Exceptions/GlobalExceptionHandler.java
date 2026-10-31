package com.example.BancoDeDados.Exceptions;


import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(EmailJaCadastradoException.class)
  public ResponseEntity<String> handleEmailDuplicado(EmailJaCadastradoException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(ex.getMessage());
  }
}
