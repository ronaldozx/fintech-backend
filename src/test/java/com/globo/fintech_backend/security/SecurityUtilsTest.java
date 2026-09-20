package com.globo.fintech_backend.security;

import com.globo.fintech_backend.exception.UnauthenticatedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecurityUtilsTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsIdOfAuthenticatedUser() {
        AuthenticatedUser principal = new AuthenticatedUser(7L, "user@example.com");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList())
        );

        assertEquals(7L, SecurityUtils.getLoggedUserId());
    }

    @Test
    void throwsWhenNobodyIsAuthenticated() {
        assertThrows(UnauthenticatedException.class, SecurityUtils::getLoggedUserId);
    }

    @Test
    void authenticationNameIsTheEmail() {
        AuthenticatedUser principal = new AuthenticatedUser(7L, "user@example.com");
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(principal, null, Collections.emptyList());

        assertEquals("user@example.com", auth.getName());
    }
}
