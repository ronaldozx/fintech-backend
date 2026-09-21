package com.globo.fintech_backend.security;

import com.globo.fintech_backend.Auth.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class SecurityConfigTest {

    private final SecurityConfig config = new SecurityConfig(mock(JwtService.class), List.of("http://localhost:5173"));

    private CorsConfiguration corsFor(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", path);
        return config.corsConfigurationSource().getCorsConfiguration(request);
    }

    @Test
    void theBrowserIsAllowedToUseEveryMethodTheApiExposes() {
        CorsConfiguration cors = corsFor("/transaction/1");

        assertNotNull(cors);
        for (HttpMethod method : List.of(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE)) {
            assertTrue(cors.getAllowedMethods().contains(method.name()), method + " must be allowed");
        }
    }

    @Test
    void onlyTheConfiguredOriginsAreAllowed() {
        assertTrue(corsFor("/budgets").getAllowedOrigins().equals(List.of("http://localhost:5173")));
    }
}
