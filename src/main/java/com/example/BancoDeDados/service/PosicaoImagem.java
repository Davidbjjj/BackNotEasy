package com.example.BancoDeDados.service;

/**
 * Posição de uma imagem na página do PDF.
 * topo/base são proporções da altura da página (0 = topo, 1 = rodapé).
 */
public record PosicaoImagem(int pagina, double topo, double base) {

    public double centro() {
        return (topo + base) / 2.0;
    }
}
