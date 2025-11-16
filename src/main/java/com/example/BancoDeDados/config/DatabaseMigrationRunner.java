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
        // Ajustes de tipos para textos longos
        apply("ALTER TABLE IF EXISTS questao_alternativas ALTER COLUMN alternativas TYPE TEXT");
        apply("ALTER TABLE IF EXISTS questao ALTER COLUMN cabecalho TYPE TEXT");
        apply("ALTER TABLE IF EXISTS questao ALTER COLUMN enunciado TYPE TEXT");

        // Índices para performance
        apply("CREATE INDEX IF NOT EXISTS idx_questao_lista_id ON questao (lista_id)");
        apply("CREATE INDEX IF NOT EXISTS idx_questao_lista_id_id ON questao (lista_id, id)");
        apply("CREATE INDEX IF NOT EXISTS idx_questao_alternativas_questao_id ON questao_alternativas (questao_id)");
    }

    private void apply(String sql) {
        try {
            jdbcTemplate.execute(sql);
            log.info("Applied migration SQL: {}", sql);
        } catch (Exception ex) {
            // Se já estiver aplicado ou a coluna/tabela não existir, apenas registra aviso e segue
            log.warn("Could not apply migration SQL: {} -> {}", sql, ex.getMessage());
        }
    }
}
