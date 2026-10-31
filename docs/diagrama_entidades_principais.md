# Diagrama de Entidades Principais - Sistema NotEasy

Este documento apresenta apenas as entidades principais do sistema NotEasy com foco nos relacionamentos essenciais.

---

## Diagrama ER Simplificado (PlantUML)

```plantuml
@startuml

' Configurações
skinparam linetype ortho
skinparam class {
    BackgroundColor WhiteSmoke
    BorderColor Black
    ArrowColor Black
}

' ==================== ENTIDADES PRINCIPAIS ====================

class Instituicao {
    + id: UUID (PK)
    + nome: String
    + email: String (unique)
    + senha: String
}

class Professor {
    + id: UUID (PK)
    + nome: String
    + email: String (unique)
    + senha: String
    + instituicao_id: UUID (FK)
}

class Estudante {
    + id: UUID (PK)
    + nome: String
    + email: String (unique)
    + senha: String
}

class Disciplina {
    + id: UUID (PK)
    + nome: String
    + instituicao_id: UUID (FK)
    + professor_id: UUID (FK)
}

class Lista {
    + id: UUID (PK)
    + titulo: String
    + professor_id: UUID (FK)
    + disciplina_id: UUID (FK)
}

class Questao {
    + id: Integer (PK)
    + cabecalho: TEXT
    + enunciado: TEXT
    + gabarito: Integer
    + lista_id: UUID (FK)
}

class QuestaoAlternativa {
    + id: Long (PK)
    + questao_id: Integer (FK)
    + texto: TEXT
    + ordem: Integer
}

class QuestaoImagem {
    + id: Long (PK)
    + questao_id: Integer (FK)
    + nomeArquivo: String
    + dadosImagem: BYTEA
    + tipoMime: String
    + ordem: Integer
    + textoOcr: TEXT
}

class RespostaEstudantes {
    + id: UUID (PK)
    + estudante_id: UUID (FK)
    + questao_id: Integer (FK)
    + resposta: Integer
    + correta: Boolean
}

class Evento {
    + id: UUID (PK)
    + titulo: String
    + notaMaxima: Double
    + data: LocalDateTime
    + disciplina_id: UUID (FK)
    + professor_id: UUID (FK)
}

' ==================== RELACIONAMENTOS ====================

Instituicao "1" -- "0..*" Professor
Instituicao "1" -- "0..*" Disciplina

Professor "1" -- "0..*" Lista
Professor "1" -- "0..*" Disciplina
Professor "1" -- "0..*" Evento

Disciplina "0..*" -- "0..*" Estudante
Disciplina "1" -- "0..*" Evento
Disciplina "1" -- "0..*" Lista

Lista "0..*" -- "0..*" Estudante
Lista "1" -- "0..*" Questao

Questao "1" -- "0..*" QuestaoAlternativa
Questao "1" -- "0..*" QuestaoImagem
Questao "1" -- "0..*" RespostaEstudantes

Estudante "1" -- "0..*" RespostaEstudantes

Evento "0..*" -- "0..*" Lista

@enduml
```

---

## Diagrama simplificado (ASCII)

```
                    ┌──────────────┐
                    │ Instituicao  │
                    └──────┬───────┘
                           │
              ┌────────────┼────────────┐
              │                         │
       ┌──────▼───────┐          ┌─────▼──────┐
       │  Professor   │─────────►│ Disciplina │
       └──────┬───────┘          └─────┬──────┘
              │                        │
              │                   ┌────┼─────┐
              │                   │    │     │ N:N
              │                   │    │ ┌───▼──────┐
              │                   │    │ │ Estudante│
       ┌──────┼───────┐           │    │ └──────────┘
       │      │       │           │    │
   ┌───▼──┐ ┌▼─────┐ │      ┌────▼────▼┐
   │Lista │ │Evento│◄┼──────┤  N:N     │
   └───┬──┘ └──────┘ │      └──────────┘
       │ 1:N         │
   ┌───▼────────┐    │
   │  Questao   │    │
   └───┬────────┘    │
       │             │
   ┌───┼─────────────┼──────┐
   │   │             │      │
┌──▼──┐ ┌──▼──────┐ ┌▼──────────┐
│Alter│ │ Imagem  │ │  Resposta │
│nativ│ │         │ │ Estudantes│
└─────┘ └─────────┘ └───────────┘
```

---

## Descrição das Entidades Principais

### 🏢 Instituicao
Organização educacional que possui professores e oferece disciplinas.

### 👨‍🏫 Professor
Docente que leciona disciplinas e cria listas de questões.
- FK: `instituicao_id`

### 👨‍🎓 Estudante
Aluno que resolve questões e recebe avaliações.
- Relacionamento N:N com Disciplina (matrícula)
- Relacionamento N:N com Lista (atribuída)

### 📚 Disciplina
Matéria lecionada por um professor em uma instituição.
- FK: `instituicao_id`, `professor_id`

### 📋 Lista
Conjunto de questões criado por um professor no contexto de uma disciplina.
- FK: `professor_id`, `disciplina_id`
- Relacionamento N:1 com Professor (criador)
- Relacionamento N:1 com Disciplina (contexto acadêmico)

### ❓ Questao
Pergunta com enunciado, alternativas e imagens.
- FK: `lista_id`
- Campo `gabarito` indica o índice da resposta correta

### 📝 QuestaoAlternativa
Opções de resposta (a, b, c, d, e).
- FK: `questao_id`
- Campo `ordem` define a sequência (0=a, 1=b...)

### 🖼️ QuestaoImagem
Imagem armazenada como BLOB no banco.
- FK: `questao_id`
- Campo `dadosImagem` (BYTEA) contém os bytes da imagem
- Campo `textoOcr` armazena texto extraído por OCR

### ✅ RespostaEstudantes
Resposta de um estudante a uma questão.
- FK: `estudante_id`, `questao_id`
- Campo `correta` indica se acertou

### 📅 Evento
Prova, simulado ou atividade avaliativa vinculado a uma disciplina e criado por um professor.
- FK: `disciplina_id`, `professor_id`
- Relacionamento N:1 com Disciplina
- Relacionamento N:1 com Professor
- Relacionamento N:N com Lista (via ListaEvento)

---

## Fluxo Principal

```
1. Instituicao → cria → Professor
2. Professor → leciona → Disciplina
3. Estudante → matricula-se → Disciplina
4. Professor → cria → Lista (vinculada à Disciplina)
5. Lista → atribuída → Estudante
6. Lista → contém → Questao
7. Questao → possui → QuestaoAlternativa + QuestaoImagem
8. Estudante → responde → Questao (gera RespostaEstudantes)
9. Professor → cria → Evento (vinculado à Disciplina)
10. Evento → vincula → Lista (via ListaEvento)
```

---

## Relacionamentos Principais

| Origem | Relacionamento | Destino | Tipo |
|--------|---------------|---------|------|
| Instituicao | possui | Professor | 1:N |
| Instituicao | oferece | Disciplina | 1:N |
| Professor | cria | Lista | 1:N |
| Professor | leciona | Disciplina | 1:N |
| Professor | cria | Evento | 1:N |
| Disciplina | matricula | Estudante | N:N |
| Disciplina | possui | Evento | 1:N |
| Disciplina | possui | Lista | 1:N |
| Lista | atribuída | Estudante | N:N |
| Lista | contém | Questao | 1:N |
| Questao | possui | QuestaoAlternativa | 1:N |
| Questao | possui | QuestaoImagem | 1:N |
| Questao | recebe | RespostaEstudantes | 1:N |
| Estudante | responde | RespostaEstudantes | 1:N |
| Evento | vincula | Lista | N:N |

---

## Pontos-chave de Implementação

### 🔐 Autenticação
- Professor, Estudante e Instituicao implementam `UserDetails` (Spring Security)
- Cada um retorna authority específica (ROLE_PROFESSOR, ROLE_ESTUDANTE, etc)

### 💾 Armazenamento de Imagens
- Tipo: **BYTEA** (PostgreSQL) → `byte[]` (Java)
- Anotação: `@Column(columnDefinition = "bytea")`
- Servir ao front: endpoint dedicado com Content-Type correto + cache (ETag/304)

### 🗑️ Cascades
- QuestaoAlternativa e QuestaoImagem usam `cascade = CascadeType.ALL` e `orphanRemoval = true`
- Deletar questão remove automaticamente alternativas e imagens

### ⚡ Performance
- Relacionamentos usam `FetchType.LAZY`
- Índices em `lista_id` e `gabarito` na tabela Questao
- Batch size 50 para alternativas

---

**Arquivo**: `docs/diagrama_entidades_principais.md`  
**Data**: 22 de Novembro de 2025  
**Sistema**: NotEasy - Plataforma de questões educacionais

**Total de Entidades Principais**: 10

