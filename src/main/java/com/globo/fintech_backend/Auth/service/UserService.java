package com.globo.fintech_backend.Auth.service;

import com.globo.fintech_backend.Auth.dto.*;
import com.globo.fintech_backend.Auth.entity.User;
import com.globo.fintech_backend.Auth.exception.InvalidCredentialsException;
import com.globo.fintech_backend.Auth.mapper.UserMapper;
import com.globo.fintech_backend.Auth.repository.UserRepository;
import com.globo.fintech_backend.exception.BadRequestException;
import com.globo.fintech_backend.exception.ResourceNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.regex.Pattern;

@Service
public class UserService {
    static final int MIN_PASSWORD_LENGTH = 8;
    static final int MAX_NAME_LENGTH = 150;
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

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
        validateProfile(dto);
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

    public void changePassword(Long userId, ChangePasswordDTO dto) {
        if (dto.currentPassword() == null || dto.newPassword() == null) {
            throw new BadRequestException("Informe a senha atual e a nova senha");
        }
        if (dto.newPassword().length() < MIN_PASSWORD_LENGTH) {
            throw new BadRequestException("A nova senha precisa ter pelo menos " + MIN_PASSWORD_LENGTH + " caracteres");
        }

        User user = repository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!passwordEncoder.matches(dto.currentPassword(), user.getPassword())) {
            throw new BadRequestException("Senha atual incorreta");
        }
        if (passwordEncoder.matches(dto.newPassword(), user.getPassword())) {
            throw new BadRequestException("A nova senha precisa ser diferente da atual");
        }

        user.setPassword(passwordEncoder.encode(dto.newPassword()));
        user.setUpdatedAt(LocalDateTime.now());
        repository.save(user);
    }

    static void validateProfile(UpdateDTO dto) {
        String email = dto.getEmail() == null ? "" : dto.getEmail().trim();
        if (!EMAIL.matcher(email).matches()) {
            throw new BadRequestException("Informe um e-mail válido");
        }
        if (dto.getFullName() == null || dto.getFullName().isBlank()) {
            throw new BadRequestException("Informe o nome");
        }
        if (dto.getFullName().trim().length() > MAX_NAME_LENGTH) {
            throw new BadRequestException("O nome pode ter no máximo " + MAX_NAME_LENGTH + " caracteres");
        }
        if (dto.getMonthlyIncome() != null && (dto.getMonthlyIncome() < 0 || dto.getMonthlyIncome().isNaN() || dto.getMonthlyIncome().isInfinite())) {
            throw new BadRequestException("A renda mensal não pode ser negativa");
        }
        if (dto.getBirthDate() != null && dto.getBirthDate().isAfter(LocalDate.now())) {
            throw new BadRequestException("A data de nascimento não pode estar no futuro");
        }
        dto.setEmail(email);
        dto.setFullName(dto.getFullName().trim());
    }

    public LoginResponseDTO getCurrentUser(Long userId) {
        User user = repository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return mapper.toDTO(user);
    }
}
