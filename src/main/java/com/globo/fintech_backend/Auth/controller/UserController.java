package com.globo.fintech_backend.Auth.controller;

import com.globo.fintech_backend.Auth.dto.*;
import com.globo.fintech_backend.Auth.service.UserService;
import com.globo.fintech_backend.security.SecurityUtils;
import org.springframework.http.ResponseEntity;
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
        return ResponseEntity.ok(service.update(SecurityUtils.getLoggedUserId(), dto));
    }

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@RequestBody ChangePasswordDTO dto){
        service.changePassword(SecurityUtils.getLoggedUserId(), dto);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<LoginResponseDTO> me() {
        return ResponseEntity.ok(service.getCurrentUser(SecurityUtils.getLoggedUserId()));
    }
}
