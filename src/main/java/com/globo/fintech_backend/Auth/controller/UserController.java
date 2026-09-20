package com.globo.fintech_backend.Auth.controller;

import com.globo.fintech_backend.Auth.dto.*;
import com.globo.fintech_backend.Auth.service.UserService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class UserController {
    UserService service;

    public UserController(UserService service){
        this.service = service;
    }


    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@RequestBody LoginDTO dto) {
        return ResponseEntity.ok(service.login(dto));
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponseDTO> register(@RequestBody RegisterDTO dto){
        return ResponseEntity.ok(service.register(dto));
    }

    @PutMapping("/update")
    public ResponseEntity<UpdateResponseDTO> update(@RequestBody UpdateDTO dto){
        return ResponseEntity.ok(service.update(dto));
    }

    @GetMapping("/me")
    public ResponseEntity<LoginResponseDTO> me(Authentication auth) {
        return ResponseEntity.ok(service.getCurrentUser(auth.getName()));
    }
}
