# Análise de Cardinalidades - Sistema NotEasy

Este documento analisa as cardinalidades dos relacionamentos entre entidades, verificando consistência lógica e conformidade com regras de negócio.

---

## ✅ Cardinalidades Corretas

### 1. **Instituicao → Professor** (1:N)
- ✅ **Correto**: Uma instituição possui vários professores
- ✅ **Código**: Professor tem FK `instituicao_id NOT NULL`
- ✅ **Lógica de negócio**: Um professor pertence a uma única instituição

### 2. **Instituicao → Disciplina** (1:N)
- ✅ **Correto**: Uma instituição oferece várias disciplinas
- ✅ **Código**: Disciplina tem FK `instituicao_id NOT NULL`
- ✅ **Lógica de negócio**: Uma disciplina é oferecida por uma única instituição

### 3. **Professor → Lista** (1:N)
- ✅ **Correto**: Um professor cria várias listas
- ✅ **Código**: Lista tem FK `professor_id NOT NULL`
- ✅ **Lógica de negócio**: Uma lista é criada por um único professor

### 4. **Professor → Disciplina** (1:N)
- ✅ **Correto**: Um professor leciona várias disciplinas
- ✅ **Código**: Disciplina tem FK `professor_id NOT NULL`
- ✅ **Lógica de negócio**: Uma disciplina é lecionada por um único professor (no contexto atual)

### 5. **Disciplina → Lista** (1:N)
- ✅ **Correto**: Uma disciplina possui várias listas de questões
- ✅ **Código**: Lista tem `@ManyToOne` com Disciplina (`disciplina_id`)
- ✅ **Lógica de negócio**: Uma lista pertence a uma única disciplina (contexto acadêmico)

### 6. **Professor → Evento** (1:N)
- ✅ **Correto**: Um professor cria vários eventos
- ✅ **Código**: Evento tem `@ManyToOne` com Professor
- ✅ **Lógica de negócio**: Um evento é criado por um único professor

### 7. **Disciplina → Evento** (1:N)
- ✅ **Correto**: Uma disciplina possui vários eventos
- ✅ **Código**: Evento tem `@ManyToOne` com Disciplina
- ✅ **Lógica de negócio**: Um evento pertence a uma única disciplina

### 8. **Disciplina ↔ Estudante** (N:N)
- ✅ **Correto**: Estudantes se matriculam em várias disciplinas e disciplinas têm vários estudantes
- ✅ **Código**: `@ManyToMany` com tabela de junção `disciplina_estudantes`
- ✅ **Lógica de negócio**: Matrícula típica de sistema acadêmico

### 9. **Lista ↔ Estudante** (N:N)
- ✅ **Correto**: Listas podem ser atribuídas a vários estudantes e estudantes recebem várias listas
- ✅ **Código**: `@ManyToMany` com tabela de junção `lista_estudantes`
- ✅ **Lógica de negócio**: Distribuição de atividades para turma

### 10. **Lista → Questao** (1:N)
- ✅ **Correto**: Uma lista contém várias questões
- ✅ **Código**: Questao tem `@ManyToOne` com Lista (`lista_id NOT NULL`)
- ✅ **Lógica de negócio**: Uma questão pertence a uma única lista

### 11. **Questao → QuestaoAlternativa** (1:N)
- ✅ **Correto**: Uma questão possui várias alternativas
- ✅ **Código**: `@OneToMany` com cascade ALL e orphanRemoval
- ✅ **Lógica de negócio**: Alternativas pertencem exclusivamente a uma questão

### 12. **Questao → QuestaoImagem** (1:N)
- ✅ **Correto**: Uma questão pode ter várias imagens
- ✅ **Código**: `@OneToMany` com cascade ALL e orphanRemoval
- ✅ **Lógica de negócio**: Imagens pertencem exclusivamente a uma questão

### 13. **Questao → RespostaEstudantes** (1:N)
- ✅ **Correto**: Uma questão recebe várias respostas de diferentes estudantes
- ✅ **Código**: RespostaEstudantes tem FK `questao_id`
- ✅ **Lógica de negócio**: Cada estudante responde a mesma questão

### 14. **Estudante → RespostaEstudantes** (1:N)
- ✅ **Correto**: Um estudante responde várias questões
- ✅ **Código**: RespostaEstudantes tem FK `estudante_id`
- ✅ **Lógica de negócio**: Histórico de respostas do estudante

### 15. **Evento ↔ Lista** (N:N)
- ✅ **Correto**: Um evento pode usar várias listas e uma lista pode ser usada em vários eventos
- ✅ **Código**: Tabela de junção `ListaEvento`
- ✅ **Lógica de negócio**: Reutilização de questões em diferentes provas/simulados

---

## 🟡 Cardinalidades que Podem Gerar Dúvidas (mas estão corretas)

### 1. **Professor → Disciplina** (1:N) vs N:N
**Estado atual**: 1:N (um professor por disciplina)

**Discussão**:
- ✅ **Vantagem**: Simplicidade de implementação
- ⚠️ **Limitação**: Na vida real, uma disciplina pode ter vários professores (aulas teóricas vs práticas, substituições, etc)
- 💡 **Sugestão futura**: Considerar mudar para N:N se necessário

### 2. **Lista → Questao** (1:N) vs N:N
**Estado atual**: 1:N (questão pertence a uma única lista)

**Discussão**:
- ✅ **Vantagem**: Controle mais rígido sobre questões
- ⚠️ **Limitação**: Questão não pode ser reutilizada em múltiplas listas (precisa duplicar)
- 💡 **Sugestão futura**: Se reutilização de questões for importante, migrar para N:N com tabela de junção

---

## 🔴 Inconsistências Encontradas e Corrigidas

### ❌ Problema 1: Tipo de FK incompatível
**Erro encontrado**: 
- `Lista.id` é **UUID**
- Documentação mostrava `Questao.lista_id` como **Integer**

**Correção aplicada**: 
- ✅ Atualizado para `lista_id: UUID (FK)` na documentação

### ❌ Problema 2: Relacionamento Evento não documentado
**Erro encontrado**:
- Código tem `Evento @ManyToOne Professor`
- Código tem `Evento @ManyToOne Disciplina`
- Documentação não mostrava esses relacionamentos

**Correção aplicada**:
- ✅ Adicionado `Professor "1" -- "0..*" Evento`
- ✅ Adicionado `Disciplina "1" -- "0..*" Evento`
- ✅ Atualizado fluxo principal e tabela de relacionamentos

---

## 📊 Resumo das Cardinalidades

| Relacionamento | Cardinalidade | Status |
|----------------|---------------|--------|
| Instituicao → Professor | 1:N | ✅ |
| Instituicao → Disciplina | 1:N | ✅ |
| Professor → Lista | 1:N | ✅ |
| Professor → Disciplina | 1:N | ✅ |
| Professor → Evento | 1:N | ✅ |
| Disciplina → Lista | 1:N | ✅ |
| Disciplina → Evento | 1:N | ✅ |
| Disciplina ↔ Estudante | N:N | ✅ |
| Lista ↔ Estudante | N:N | ✅ |
| Lista → Questao | 1:N | ✅ |
| Questao → QuestaoAlternativa | 1:N | ✅ |
| Questao → QuestaoImagem | 1:N | ✅ |
| Questao → RespostaEstudantes | 1:N | ✅ |
| Estudante → RespostaEstudantes | 1:N | ✅ |
| Evento ↔ Lista | N:N | ✅ |

**Total**: 15 relacionamentos principais  
**Status**: ✅ Todos validados e documentados corretamente

---

## 🎯 Validação de Regras de Negócio

### Cenário 1: Criar uma prova (Evento)
```
1. Professor → cria Evento (vinculado à Disciplina)
2. Evento → vincula Lista(s) via ListaEvento
3. Lista → contém Questao(s)
4. Estudante → responde Questao(s) → gera RespostaEstudantes
```
✅ **Cardinalidades fazem sentido!**

### Cenário 2: Reutilizar questões
```
Problema: Professor quer usar mesmas questões em 2 listas diferentes
```
⚠️ **Limitação atual**: Precisa duplicar questões (relação 1:N)  
💡 **Solução futura**: Migrar para N:N se for requisito importante

### Cenário 3: Co-docência (2 professores na mesma disciplina)
```
Problema: 2 professores lecionam a mesma disciplina
```
⚠️ **Limitação atual**: Disciplina tem FK única para professor (relação 1:N)  
💡 **Solução futura**: Migrar para N:N se for requisito importante

---

## ✅ Conclusão

**As cardinalidades fazem sentido do ponto de vista de negócio?**  
✅ **SIM!** Todas as cardinalidades estão corretas para um sistema educacional básico.

**Há inconsistências críticas?**  
✅ **NÃO!** As inconsistências encontradas eram apenas na documentação e foram corrigidas.

**Há limitações conhecidas?**  
⚠️ **SIM, mas são aceitáveis**:
- Questões não podem ser reutilizadas entre listas (1:N ao invés de N:N)
- Disciplina tem apenas um professor (1:N ao invés de N:N)

Essas limitações podem ser endereçadas no futuro se os requisitos de negócio mudarem.

---

**Arquivo**: `docs/analise_cardinalidades.md`  
**Data**: 22 de Novembro de 2025  
**Status**: ✅ Validado e documentado

