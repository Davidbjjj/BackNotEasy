package com.example.BancoDeDados.service;

/**
 * Posição de uma imagem na página do PDF.
 * topo/base são proporções da altura da página (0 = topo, 1 = rodapé);
 * esq/dir são proporções da largura (0 = margem esquerda, 1 = direita).
 */
public record PosicaoImagem(int pagina, double topo, double base, double esq, double dir) {

    /** Sem posição horizontal conhecida (a imagem ocupa a largura toda). */
    public PosicaoImagem(int pagina, double topo, double base) {
        this(pagina, topo, base, 0.0, 1.0);
    }

    public double centro() {
        return (topo + base) / 2.0;
    }
}
