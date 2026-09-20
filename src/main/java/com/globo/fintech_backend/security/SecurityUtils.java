package com.globo.fintech_backend.security;

import com.globo.fintech_backend.Auth.entity.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class SecurityUtils {

    public static Long getLoggedUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new RuntimeException("Usuário não autenticado");
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof User) {
            return ((User) principal).getId();
        }
        if (principal instanceof Long) {
            return (Long) principal;
        }

        throw new RuntimeException("Tipo de principal desconhecido");
    }
}