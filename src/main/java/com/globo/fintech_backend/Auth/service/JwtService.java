package com.globo.fintech_backend.Auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class JwtService {

    private static final String USER_ID_CLAIM = "uid";
    private static final long EXPIRATION_MILLIS = 1000L * 60 * 60;

    @Value("${jwt.secret}")
    private String secret;

    public String generateToken(Long userId, String email) {
        return Jwts.builder()
                .setSubject(email)
                .claim(USER_ID_CLAIM, userId)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_MILLIS))
                .signWith(SignatureAlgorithm.HS256, secret)
                .compact();
    }

    public String getSubject(String token) {
        return parseClaims(token).getSubject();
    }

    public Long getUserId(String token) {
        Number id = parseClaims(token).get(USER_ID_CLAIM, Number.class);
        return id == null ? null : id.longValue();
    }

    public boolean isTokenValid(String token) {
        try {
            return getUserId(token) != null;
        } catch (Exception e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .setSigningKey(secret)
                .parseClaimsJws(token)
                .getBody();
    }
}
