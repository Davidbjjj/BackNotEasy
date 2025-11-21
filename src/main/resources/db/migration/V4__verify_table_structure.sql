-- Script de diagnóstico e correção da tabela questao_imagens
-- Execute este script para verificar e corrigir problemas de tipo

-- 1. Verificar estrutura atual da tabela
SELECT
    column_name,
    data_type,
    is_nullable,
    column_default
FROM information_schema.columns
WHERE table_name = 'questao_imagens'
ORDER BY ordinal_position;

-- 2. Verificar se há dados na tabela
SELECT COUNT(*) as total_registros FROM questao_imagens;

-- 3. Se a coluna dados_imagem estiver com tipo errado, corrigir:
-- ALTER TABLE questao_imagens DROP COLUMN IF EXISTS dados_imagem;
-- ALTER TABLE questao_imagens ADD COLUMN dados_imagem BYTEA;

-- 4. Garantir que tipo_mime não seja NULL
UPDATE questao_imagens SET tipo_mime = 'image/png' WHERE tipo_mime IS NULL;
ALTER TABLE questao_imagens ALTER COLUMN tipo_mime SET NOT NULL;

-- 5. Verificar estrutura final
SELECT
    column_name,
    data_type,
    character_maximum_length,
    is_nullable
FROM information_schema.columns
WHERE table_name = 'questao_imagens'
  AND column_name IN ('dados_imagem', 'tipo_mime', 'tamanho_bytes')
ORDER BY ordinal_position;

