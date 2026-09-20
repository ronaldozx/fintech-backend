package com.globo.fintech_backend.Auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtServiceTest {

    private static final String SECRET = "dGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQ=";
    private static final String OTHER_SECRET = "b3V0cmEtY2hhdmUtb3V0cmEtY2hhdmUtb3V0cmEtY2hhdmU=";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret", SECRET);
    }

    @Test
    void tokenCarriesEmailAndUserId() {
        String token = jwtService.generateToken(42L, "user@example.com");

        assertTrue(jwtService.isTokenValid(token));
        assertEquals("user@example.com", jwtService.getSubject(token));
        assertEquals(42L, jwtService.getUserId(token));
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        JwtService other = new JwtService();
        ReflectionTestUtils.setField(other, "secret", OTHER_SECRET);

        String token = other.generateToken(1L, "user@example.com");

        assertFalse(jwtService.isTokenValid(token));
    }

    @Test
    void garbageTokenIsRejected() {
        assertFalse(jwtService.isTokenValid("not-a-token"));
    }
}
