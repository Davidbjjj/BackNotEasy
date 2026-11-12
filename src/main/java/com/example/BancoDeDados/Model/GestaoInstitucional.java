package com.example.BancoDeDados.Model;

/**
 * Constantes do módulo de gestão da instituição (FE-301 a FE-309). As entidades ficam em arquivos próprios;
 * aqui só os papéis e as permissões, para o código inteiro usar os mesmos nomes.
 */
public final class GestaoInstitucional {

    private GestaoInstitucional() {}

    public static final String ADMIN = "ADMIN";
    public static final String COORDENADOR = "COORDENADOR";
    public static final String PROFESSOR = "PROFESSOR";

    // Permissões (chaves estáveis usadas pelo front para esconder itens de menu)
    public static final String VER_DASHBOARD = "VER_DASHBOARD";
    public static final String VER_RELATORIOS = "VER_RELATORIOS";
    public static final String IMPORTAR_USUARIOS = "IMPORTAR_USUARIOS";
    public static final String GERIR_CONVITES = "GERIR_CONVITES";
    public static final String GERIR_PERIODOS = "GERIR_PERIODOS";
    public static final String EMITIR_BOLETINS = "EMITIR_BOLETINS";
    public static final String EDITAR_CONFIGURACOES = "EDITAR_CONFIGURACOES";
    public static final String GERIR_PAPEIS = "GERIR_PAPEIS";
    public static final String VER_AUDITORIA = "VER_AUDITORIA";
    public static final String CORRIGIR = "CORRIGIR";
    public static final String BANCO_QUESTOES = "BANCO_QUESTOES";
    public static final String VER_ALUNOS_RISCO = "VER_ALUNOS_RISCO";
    public static final String EXPORTAR_NOTAS = "EXPORTAR_NOTAS";
}
