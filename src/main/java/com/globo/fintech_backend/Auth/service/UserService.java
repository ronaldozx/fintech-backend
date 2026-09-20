package com.globo.fintech_backend.Auth.service;

import com.globo.fintech_backend.Auth.dto.*;
import com.globo.fintech_backend.Auth.entity.User;
import com.globo.fintech_backend.Auth.exception.InvalidCredentialsException;
import com.globo.fintech_backend.Auth.mapper.UserMapper;
import com.globo.fintech_backend.Auth.repository.UserRepository;
import com.globo.fintech_backend.exception.ResourceNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class UserService {
    private final UserRepository repository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper mapper;

    public UserService(UserRepository repository, JwtService jwtService, PasswordEncoder passwordEncoder, UserMapper mapper){
        this.repository = repository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
        this.mapper = mapper;
    }

    public LoginResponseDTO login(LoginDTO dto){
        User user = repository.findByEmail(dto.getEmail());

        if(user == null){
            throw new InvalidCredentialsException("Email address invalid");
        }

        if(!passwordEncoder.matches(dto.getPassword(), user.getPassword())){
            throw new InvalidCredentialsException("Password invalid");
        }

        String token = jwtService.generateToken(user.getId(), user.getEmail());

        LoginResponseDTO response = new LoginResponseDTO();
        response.setToken(token);
        response.setId(user.getId());
        response.setEmail(user.getEmail());
        response.setBirthDate(user.getBirthDate());
        response.setFullName(user.getFullName());
        response.setMonthlyIncome(user.getMonthlyIncome());

        return response;
    }

    public RegisterResponseDTO register(RegisterDTO dto){
        User user = new User();
        if (repository.findByEmail(dto.getEmail()) != null) {
            throw new InvalidCredentialsException("Email duplicate");
        }
        user.setEmail(dto.getEmail());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setFullName(dto.getFullName());
        user.setBirthDate(dto.getBirthDate());
        user.setMonthlyIncome(dto.getMonthlyIncome());
        user.setCreatedAt(LocalDateTime.now());
        User savedUser = repository.save(user);
        String token = jwtService.generateToken(savedUser.getId(), savedUser.getEmail());

        RegisterResponseDTO auth = new RegisterResponseDTO();
        auth.setId(savedUser.getId());
        auth.setEmail(savedUser.getEmail());
        auth.setToken(token);

        return auth;
    }

    public UpdateResponseDTO update(Long userId, UpdateDTO dto){
        User user = repository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));

        User existing = repository.findByEmail(dto.getEmail());
        if (existing != null && !existing.getId().equals(userId)) {
            throw new InvalidCredentialsException("Email duplicate");
        }

        user.setEmail(dto.getEmail());
        user.setFullName(dto.getFullName());
        user.setBirthDate(dto.getBirthDate());
        user.setMonthlyIncome(dto.getMonthlyIncome());
        user.setUpdatedAt(LocalDateTime.now());

        repository.save(user);

        UpdateResponseDTO response = new UpdateResponseDTO();
        response.setId(user.getId());
        response.setEmail(user.getEmail());
        response.setMonthlyIncome(user.getMonthlyIncome());
        response.setUpdatedAt(user.getUpdatedAt());

        return response;
    }

    public LoginResponseDTO getCurrentUser(Long userId) {
        User user = repository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return mapper.toDTO(user);
    }
}
