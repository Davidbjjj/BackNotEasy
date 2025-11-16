-- Migrate questao_alternativas.alternativas to TEXT and questao.cabecalho/enunciado to TEXT
ALTER TABLE IF EXISTS questao_alternativas
  ALTER COLUMN alternativas TYPE TEXT;

ALTER TABLE IF EXISTS questao
  ALTER COLUMN cabecalho TYPE TEXT,
  ALTER COLUMN enunciado TYPE TEXT;

