# Documentação de Segurança - BackEnd NotEasy

Este documento descreve a arquitetura de segurança atual do projeto, componentes principais, fluxo de autenticação, políticas de autorização, CORS, práticas adotadas e recomendações de melhoria.

---
## 1. Visão Geral

O sistema utiliza Spring Security em modo stateless com autenticação baseada em **JWT (Bearer Token)**. Os tokens são gerados pelo serviço `TokenService` e validados a cada requisição por um filtro customizado (`SecurityFilter`). A aplicação não mantém sessão no servidor (`SessionCreationPolicy.STATELESS`).

Componentes principais:
- `Configurations` (SecurityFilterChain): configura pipelines de segurança.
- `SecurityFilter`: filtro `OncePerRequestFilter` que extrai e valida JWT.
- `TokenService`: gera, valida e revoga tokens JWT.
- `CorsConfig`: configuração global de CORS.
- `PasswordEncoder`: BCrypt para hash de senhas.
- `CustomUserDetailsService` (não mostrado aqui): integra com persistência de usuários (Accounts/Perfis).

---
## 2. Fluxo de Autenticação (Simplificado)

```
Login Controller (gera JWT) -> Front armazena -> Front envia Authorization: Bearer <token>
      ↓
SecurityFilter -> extrai token -> verifica revogação -> valida assinatura/issuer ->
      ↓
Se válido: cria Authentication com GrantedAuthority -> SecurityContextHolder
      ↓
Controller protegido acessa dados -> resposta
```

Passos internos no `SecurityFilter`:
1. Leitura do header `Authorization`.
2. Verificação prefixo `Bearer `.
3. Checagem de revogação (`tokenService.isTokenRevoked`).
4. Validação do token (`tokenService.validarToken`).
5. Criação de `UsernamePasswordAuthenticationToken` com autoridade estática `ROLE_ADM` (⚠ ver melhoria).
6. Registro no `SecurityContextHolder`.

---
## 3. Geração de Token (TokenService)

Características do token:
- Assinatura: HMAC256 com segredo fixo (`"1234"`).
- Issuer: `BancoDeQuestoes`.
- Claims adicionados condicionalmente conforme o perfil do `Account`:
  - `role` (Enum de perfil: PROFESSOR, ESTUDANTE, etc.)
  - `userId`
  - `nome`
  - `instituicaoId` (se existir)
  - `instituicaoNome` (para perfil de instituição)
- Expiração: 2 horas (`plusHours(2)`).

Funções de extração disponíveis:
- `getUserIdFromToken`
- `getUserTypeFromToken` (retorna claim `userType` – aparentemente não setada na geração, ⚠ inconsistência)
- `getInstituicaoIdFromToken`
- Revogação: `revokeToken(token)` + manutenção em `revokedTokens (HashSet)`.

---
## 4. Autorização (SecurityFilterChain)

Configuração atual (`Configurations`):
```java
.authorizeHttpRequests(authorize -> authorize
    .requestMatchers(HttpMethod.POST, "/pdf/processar-salvar").hasRole("PROFESSOR")
    .anyRequest().permitAll()
)
```

Impacto:
- Apenas `POST /pdf/processar-salvar` exige autenticação com papel `ROLE_PROFESSOR`.
- Todos os demais endpoints estão liberados (`permitAll`).

⚠ Riscos:
- Endpoints sensíveis não são protegidos (ex: criação de listas, questões, geração de eventos, etc.).
- `SecurityFilter` injeta autoridade fixa `ROLE_ADM` independente do token real → pode burlar restrições de papel.

---
## 5. Filtro de Segurança (`SecurityFilter`)

Pontos atuais:
- Usa `TokenService` para validar e verificar revogação.
- Ignora construir usuário real (principal = `null`).
- Injeta sempre `ROLE_ADM` como GrantedAuthority.

Recomendação:
- Construir Authentication com `principal = UserDetails` (carregado via `CustomUserDetailsService`).
- Usar claim `role` do token para montar autoridades dinâmicas.
- Adicionar verificação de expiração (atualmente depende da exceção do JWT, OK, mas pode logar motivo).

---
## 6. CORS (`CorsConfig`)

Configuração atual:
```java
registry.addMapping("/**")
    .allowedOrigins("*")
    .allowedMethods("*")
    .allowedHeaders("*")
    .allowCredentials(false);
```

Observações:
- CORS totalmente aberto (origem `*`).
- `allowCredentials(false)` → não envia cookies/headers auth cross-site. OK para JWT em header.
- Produção: restringir `allowedOrigins` (ex: `https://app.noreal.com`).

---
## 7. Hash de Senhas

- Implementação: `BCryptPasswordEncoder` (padrão forte com salt).
- Recomendação: Configurar strength (ex: `new BCryptPasswordEncoder(12)`).

---
## 8. Revogação de Tokens

- Implementação simples in-memory (`Set<String> revokedTokens`).
- Limitações:
  - Perde revogações ao reiniciar servidor.
  - Escalabilidade insuficiente em múltiplas instâncias.

Sugestão de melhoria:
- Usar store externo (Redis) com TTL = expiração do token.
- Adicionar endpoint `/auth/logout` que chama `revokeToken`.

---
## 9. Possíveis Vulnerabilidades / Inconsistências

| Área | Observação | Risco | Ação proposta |
|------|------------|-------|---------------|
| Autoridades | Filtro injeta `ROLE_ADM` fixo | Elevação indevida de privilégio | Usar claim `role` do token |
| Cobertura | Apenas 1 endpoint protegido | Ampliação de ataque | Definir regras granulares |
| Segredo | `secret = "1234"` hardcoded | Fácil de vazar/bruteforce | Mover para env var + rotação |
| Claims | `userType` lido mas nunca setado | Inconsistência | Ajustar geração ou remover uso |
| Revogação | In-memory Set | Não persiste / não escala | Migrar para Redis / DB |
| Principal | Authentication com principal null | Difícil auditoria | Incluir ID/username no principal |
| CORS | `*` aberto | Exposição indevida em produção | Restringir domínio confiável |
| Token TTL | 2h fixo | Pode ser longo para perfis sensíveis | Diferenciar por role |

---
## 10. Sugestões de Melhoria (Prioridades)

1. Substituir autoridade fixa no `SecurityFilter` por leitura dinâmica de `role`.
2. Carregar `Account` via `CustomUserDetailsService` e setar no principal.
3. Mover `secret` para `application.properties` / variável de ambiente (`SPRING_JWT_SECRET`).
4. Implementar proteção adicional para endpoints (ex: `/listas/**`, `/questoes/**`, `/eventos/**`).
5. Adicionar endpoint de logout e persistência de revogação com Redis.
6. Revisar CORS para ambiente PROD: `allowedOrigins("https://seu-front.com")`.
7. Ajustar claim faltante (`userType`) ou remover métodos não usados.
8. Criar auditoria (ex: `HandlerInterceptor` para logar `userId`, endpoint, status HTTP).
9. Adicionar testes automatizados de segurança (MockMvc + cenários de acesso negado).
10. Considerar refresh tokens para sessões longevas.

---
## 11. Exemplo de Ajuste do SecurityFilter (Proposto)

```java
@Override
protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
    String auth = request.getHeader("Authorization");
    if (auth != null && auth.startsWith("Bearer ")) {
        String token = auth.substring(7);
        if (tokenService.isTokenRevoked(token)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        String subject = tokenService.validarToken(token);
        if (subject != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            Account account = accountRepository.findByEmail(subject).orElse(null);
            if (account != null) {
                var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + account.getRole().name()));
                var authToken = new UsernamePasswordAuthenticationToken(account, null, authorities);
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }
    }
    chain.doFilter(request, response);
}
```

---
## 12. Testes de Segurança Recomendados (MockMvc)

| Cenário | Método | Endpoint | Resultado Esperado |
|---------|--------|----------|--------------------|
| Acesso protegido sem token | POST | /pdf/processar-salvar | 403/401 |
| Acesso protegido com role incorreta | POST | /pdf/processar-salvar | 403 |
| Acesso protegido com role correta | POST | /pdf/processar-salvar | 200 |
| Token revogado | GET | /listas/{id} | 401 |
| Token expirado | GET | /listas/{id} | 401 |
| CORS preflight | OPTIONS | /listas/{id} | 200 + headers CORS |

---
## 13. Checklist de Conformidade OWASP (Resumo)

| Item | Status | Nota |
|------|--------|------|
| Senhas com hash forte | ✅ | BCrypt usado |
| Exposição de segredos | ❌ | Segredo hardcoded |
| Gestão de sessões | ✅ | Stateless JWT |
| Least privilege | ❌ | Autoridade fixa `ROLE_ADM` |
| Validação de entrada | ⚠ | Não detalhado (revisar controllers) |
| Logs/Auditoria | ❌ | Não implementado |
| Expiração de tokens | ✅ | 2h configurado |
| Revogação | ⚠ | In-memory apenas |
| CORS restrito | ❌ | Aberto total |

---
## 14. Conclusão

A base de segurança está funcional (JWT + filtro stateless + BCrypt), porém há pontos críticos a tratar para produção segura:
- Remover autoridade fixa
- Mover segredo JWT para ambiente
- Restringir endpoints e CORS
- Persistir revogação
- Incluir auditoria e testes

Com as melhorias propostas, o sistema evolui de um nível básico para um nível intermediário de segurança robusta.

---
**Arquivo**: `docs/seguranca.md`  
**Data**: 22/11/2025

