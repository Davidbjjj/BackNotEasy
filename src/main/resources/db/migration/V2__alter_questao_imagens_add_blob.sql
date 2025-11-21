-- Migração para armazenar imagens no banco de dados
-- Remove colunas de caminho de arquivo e adiciona coluna para dados binários

-- Remover coluna se já existir (para recriar com tipo correto)
ALTER TABLE questao_imagens DROP COLUMN IF EXISTS dados_imagem;

-- Adicionar coluna para dados da imagem com tipo correto (permite NULL temporariamente)
ALTER TABLE questao_imagens ADD COLUMN dados_imagem bytea;

-- OPÇÃO 1: Se você não tem imagens importantes, pode deletar registros antigos
-- DELETE FROM questao_imagens WHERE dados_imagem IS NULL;

-- OPÇÃO 2: Se preferir manter os registros, eles ficarão com dados_imagem NULL
-- Você precisará fazer upload manual das imagens novamente ou criar um script de migração

-- Remover colunas não utilizadas (caso existam)
-- Comentado para não perder dados durante desenvolvimento
-- ALTER TABLE questao_imagens DROP COLUMN IF EXISTS caminho_arquivo;
-- ALTER TABLE questao_imagens DROP COLUMN IF EXISTS url_publica;

-- Garantir que tipo_mime tenha valor padrão se for NULL
UPDATE questao_imagens SET tipo_mime = 'image/png' WHERE tipo_mime IS NULL;
ALTER TABLE questao_imagens ALTER COLUMN tipo_mime SET NOT NULL;
ALTER TABLE questao_imagens ALTER COLUMN tipo_mime SET DEFAULT 'image/png';

-- Comentários
COMMENT ON COLUMN questao_imagens.dados_imagem IS 'Dados binários da imagem armazenados no banco (NULL para registros legados)';
COMMENT ON COLUMN questao_imagens.tipo_mime IS 'Tipo MIME da imagem (ex: image/png, image/jpeg)';
COMMENT ON TABLE questao_imagens IS 'Imagens das questões armazenadas como BLOB no banco de dados';

