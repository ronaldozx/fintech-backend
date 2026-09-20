package com.globo.fintech_backend.security;

import java.security.Principal;

public record AuthenticatedUser(Long id, String email) implements Principal {

    @Override
    public String getName() {
        return email;
    }
}
