package com.example.BancoDeDados.service;

/**
 * Linha de texto do PDF com a posição em que aparece.
 * topo é a proporção da altura da página (0 = topo, 1 = rodapé).
 */
public record LinhaPdf(int pagina, double topo, String texto) {
}
