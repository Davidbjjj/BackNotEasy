package com.example.BancoDeDados.Security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/** Identidade do chamador (role e userId do JWT) para regras de acesso nos controllers. */
public final class CallerContext {

    public static final String USER_ID_ATTR = "callerUserId";

    private CallerContext() {}

    private static boolean hasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }

    public static boolean isAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal());
    }

    /** PROFESSOR, INSTITUICAO ou ADMIN: podem ver gabarito. */
    public static boolean isStaff() {
        return hasRole("PROFESSOR") || hasRole("INSTITUICAO") || hasRole("ADMIN");
    }

    public static boolean isAluno() {
        return hasRole("ALUNO");
    }

    public static boolean isAdmin() {
        return hasRole("ADMIN");
    }

    public static boolean isProfessor() {
        return hasRole("PROFESSOR");
    }

    /** 401 sem token; 403 se o chamador não for professor/instituição/admin. */
    public static void exigirStaff() {
        if (!isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Autenticação necessária");
        }
        if (!isStaff()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Apenas professor ou instituição");
        }
    }

    /** Id do professor logado; null para instituição/admin (sem escopo de professor). */
    public static UUID professorIdOuNull() {
        return isProfessor() ? userId() : null;
    }

    /** Id da instituição logada; null para professor/admin. */
    public static UUID instituicaoIdOuNull() {
        return hasRole("INSTITUICAO") ? userId() : null;
    }

    public static UUID userId() {
        Object v = RequestContextHolder.getRequestAttributes() == null ? null
                : RequestContextHolder.getRequestAttributes().getAttribute(USER_ID_ATTR, RequestAttributes.SCOPE_REQUEST);
        return v instanceof UUID u ? u : null;
    }

    /**
     * Garante que o chamador pode agir em nome do estudante: staff passa; aluno só se o
     * userId do token for o próprio estudanteId. Sem token → 401; demais perfis → 403.
     */
    public static void exigirEstudanteOuStaff(UUID estudanteId) {
        if (!isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Autenticação necessária");
        }
        if (isStaff()) return;
        if (hasRole("ALUNO") && estudanteId != null && estudanteId.equals(userId())) return;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acesso negado a dados de outro estudante");
    }
}
