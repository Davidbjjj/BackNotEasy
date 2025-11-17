package com.example.BancoDeDados.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseMigrationRunner.class);
    private final JdbcTemplate jdbcTemplate;

    public DatabaseMigrationRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("========================================");
        log.info("Iniciando migrações do banco de dados...");
        log.info("========================================");

        // Ajustes de tipos para textos longos - apenas na tabela questao
        apply("ALTER TABLE IF EXISTS questao ALTER COLUMN cabecalho TYPE TEXT");
        apply("ALTER TABLE IF EXISTS questao ALTER COLUMN enunciado TYPE TEXT");

        // Índices básicos
        apply("CREATE INDEX IF NOT EXISTS idx_questao_lista_id ON questao (lista_id)");
        apply("CREATE INDEX IF NOT EXISTS idx_questao_gabarito ON questao (gabarito)");

        // ========================================
        // MIGRAÇÃO: Refatoração questao_alternativas
        // ========================================
        log.info("Verificando necessidade de migração da tabela questao_alternativas...");

        // Verificar se precisa migrar (tabela antiga existe e nova não existe)
        boolean precisaMigrar = verificarSePrecisaMigrar();

        if (precisaMigrar) {
            log.info("✅ Iniciando migração da tabela questao_alternativas...");
            migrarQuestaoAlternativas();
            log.info("✅ Migração da tabela questao_alternativas concluída com sucesso!");
        } else {
            log.info("ℹ️ Tabela questao_alternativas já está no formato correto. Migração não necessária.");
        }

        log.info("========================================");
        log.info("Migrações concluídas!");
        log.info("========================================");
    }

    private boolean verificarSePrecisaMigrar() {
        try {
            // Verifica se a tabela questao_alternativas tem a coluna 'id' (novo formato)
            // Se tiver, já está migrada
            String checkQuery = "SELECT column_name FROM information_schema.columns " +
                              "WHERE table_name = 'questao_alternativas' AND column_name = 'id'";

            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM (" + checkQuery + ") AS subquery",
                Integer.class
            );

            return count == null || count == 0; // Precisa migrar se não tem coluna 'id'
        } catch (Exception e) {
            log.warn("Não foi possível verificar estrutura da tabela: {}", e.getMessage());
            return false; // Se der erro, assume que não precisa migrar
        }
    }

    private void migrarQuestaoAlternativas() {
        try {
            // Passo 1: Criar backup
            log.info("1/6 - Criando backup da tabela questao_alternativas...");
            apply("DROP TABLE IF EXISTS questao_alternativas_backup CASCADE");
            apply("CREATE TABLE questao_alternativas_backup AS SELECT * FROM questao_alternativas");

            // Passo 2: Criar nova tabela
            log.info("2/6 - Criando nova estrutura da tabela...");
            apply("DROP TABLE IF EXISTS questao_alternativas_new CASCADE");
            apply("CREATE TABLE questao_alternativas_new (" +
                  "id BIGSERIAL PRIMARY KEY, " +
                  "questao_id INTEGER NOT NULL, " +
                  "ordem INTEGER NOT NULL, " +
                  "texto TEXT NOT NULL)");

            // Passo 3: Migrar dados
            log.info("3/6 - Migrando dados da tabela antiga para nova estrutura...");
            apply("INSERT INTO questao_alternativas_new (questao_id, ordem, texto) " +
                  "SELECT questao_id, " +
                  "(ROW_NUMBER() OVER (PARTITION BY questao_id ORDER BY alternativas) - 1)::INTEGER AS ordem, " +
                  "alternativas AS texto " +
                  "FROM questao_alternativas_backup " +
                  "WHERE alternativas IS NOT NULL AND alternativas <> ''");

            // Passo 4: Criar índice
            log.info("4/6 - Criando índice...");
            apply("CREATE INDEX idx_questao_alternativa ON questao_alternativas_new(questao_id)");

            // Passo 5: Substituir tabela antiga
            log.info("5/6 - Substituindo tabela antiga pela nova...");
            apply("DROP TABLE questao_alternativas CASCADE");
            apply("ALTER TABLE questao_alternativas_new RENAME TO questao_alternativas");

            // Passo 6: Adicionar foreign key
            log.info("6/6 - Adicionando foreign key...");
            apply("ALTER TABLE questao_alternativas ADD CONSTRAINT fk_questao_alt " +
                  "FOREIGN KEY (questao_id) REFERENCES questao(id) ON DELETE CASCADE");

            // Verificação
            Integer totalQuestoes = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM questao", Integer.class);
            Integer totalAlternativas = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM questao_alternativas", Integer.class);
            Integer questoesSemAlternativas = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM questao q LEFT JOIN questao_alternativas qa ON qa.questao_id = q.id WHERE qa.id IS NULL",
                Integer.class);

            log.info("📊 Resultado da migração:");
            log.info("   - Total de questões: {}", totalQuestoes);
            log.info("   - Total de alternativas: {}", totalAlternativas);
            log.info("   - Questões sem alternativas: {}", questoesSemAlternativas);

        } catch (Exception e) {
            log.error("❌ Erro durante migração: {}", e.getMessage(), e);
            log.error("💡 Tentando reverter para backup...");

            try {
                apply("DROP TABLE IF EXISTS questao_alternativas CASCADE");
                apply("ALTER TABLE questao_alternativas_backup RENAME TO questao_alternativas");
                log.info("✅ Backup restaurado com sucesso!");
            } catch (Exception rollbackError) {
                log.error("❌ Erro ao reverter backup: {}", rollbackError.getMessage());
            }

            throw new RuntimeException("Falha na migração da tabela questao_alternativas", e);
        }
    }

    private void apply(String sql) {
        try {
            jdbcTemplate.execute(sql);
            log.info("Applied migration SQL: {}", sql);
        } catch (Exception ex) {
            log.warn("Could not apply migration SQL: {} -> {}", sql, ex.getMessage());
        }
    }
}
