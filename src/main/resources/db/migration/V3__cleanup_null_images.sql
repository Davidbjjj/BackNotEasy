-- Script de limpeza opcional
-- Execute este script manualmente se desejar remover imagens antigas sem dados binários

-- Visualizar quantos registros serão afetados (executar primeiro)
-- SELECT COUNT(*) FROM questao_imagens WHERE dados_imagem IS NULL;

-- Deletar apenas imagens sem dados binários (descomente para executar)
-- DELETE FROM questao_imagens WHERE dados_imagem IS NULL;

-- Tornar a coluna NOT NULL após limpar dados antigos (descomente para executar)
-- ALTER TABLE questao_imagens ALTER COLUMN dados_imagem SET NOT NULL;

-- Por enquanto, não faz nada - apenas documenta as opções
SELECT 'Migration V3: Cleanup script created. Execute commands manually if needed.' as status;

