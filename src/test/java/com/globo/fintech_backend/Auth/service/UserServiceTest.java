package com.globo.fintech_backend.Auth.service;

import com.globo.fintech_backend.Auth.dto.ChangePasswordDTO;
import com.globo.fintech_backend.Auth.dto.UpdateDTO;
import com.globo.fintech_backend.Auth.dto.UpdateResponseDTO;
import com.globo.fintech_backend.Auth.entity.User;
import com.globo.fintech_backend.Auth.exception.InvalidCredentialsException;
import com.globo.fintech_backend.Auth.mapper.UserMapper;
import com.globo.fintech_backend.Auth.repository.UserRepository;
import com.globo.fintech_backend.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final Long USER_ID = 7L;

    @Mock
    private UserRepository repository;

    @Mock
    private JwtService jwtService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private UserMapper mapper;

    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(repository, jwtService, passwordEncoder, mapper);
    }

    private static UpdateDTO profile(String email, String name, Double income, LocalDate birth) {
        UpdateDTO dto = new UpdateDTO();
        dto.setEmail(email);
        dto.setFullName(name);
        dto.setMonthlyIncome(income);
        dto.setBirthDate(birth);
        return dto;
    }

    private static User user(String encodedPassword) {
        User user = new User();
        user.setId(USER_ID);
        user.setEmail("ana@example.com");
        user.setPassword(encodedPassword);
        return user;
    }

    @Test
    void acceptsAValidProfileAndTrimsTheFields() {
        User existing = user("hash");
        when(repository.findById(USER_ID)).thenReturn(Optional.of(existing));
        when(repository.findByEmail("nova@example.com")).thenReturn(null);

        UpdateResponseDTO response = service.update(USER_ID, profile("  nova@example.com ", "  Ana Souza  ", 5000.0, LocalDate.of(1990, 1, 1)));

        assertEquals("nova@example.com", response.getEmail());
        assertEquals("Ana Souza", existing.getFullName());
        verify(repository).save(existing);
    }

    @Test
    void rejectsInvalidProfiles() {
        assertThrows(BadRequestException.class, () -> service.update(USER_ID, profile(null, "Ana", 1.0, null)));
        assertThrows(BadRequestException.class, () -> service.update(USER_ID, profile("sem-arroba", "Ana", 1.0, null)));
        assertThrows(BadRequestException.class, () -> service.update(USER_ID, profile("a@b", "Ana", 1.0, null)));
        assertThrows(BadRequestException.class, () -> service.update(USER_ID, profile("a@b.com", "  ", 1.0, null)));
        assertThrows(BadRequestException.class, () -> service.update(USER_ID, profile("a@b.com", "x".repeat(UserService.MAX_NAME_LENGTH + 1), 1.0, null)));
        assertThrows(BadRequestException.class, () -> service.update(USER_ID, profile("a@b.com", "Ana", -1.0, null)));
        assertThrows(BadRequestException.class, () -> service.update(USER_ID, profile("a@b.com", "Ana", Double.NaN, null)));
        assertThrows(BadRequestException.class, () -> service.update(USER_ID, profile("a@b.com", "Ana", 1.0, LocalDate.now().plusDays(1))));
        verify(repository, never()).save(any());
    }

    @Test
    void anEmptyIncomeAndBirthDateAreAllowed() {
        when(repository.findById(USER_ID)).thenReturn(Optional.of(user("hash")));

        service.update(USER_ID, profile("a@b.com", "Ana", null, null));

        verify(repository).save(any(User.class));
    }

    @Test
    void anEmailOfAnotherUserIsRefused() {
        when(repository.findById(USER_ID)).thenReturn(Optional.of(user("hash")));
        User other = new User();
        other.setId(99L);
        when(repository.findByEmail("a@b.com")).thenReturn(other);

        assertThrows(InvalidCredentialsException.class, () -> service.update(USER_ID, profile("a@b.com", "Ana", 1.0, null)));
    }

    @Test
    void changesThePasswordWhenTheCurrentOneMatches() {
        User existing = user("old-hash");
        when(repository.findById(USER_ID)).thenReturn(Optional.of(existing));
        when(passwordEncoder.matches("senha-antiga", "old-hash")).thenReturn(true);
        when(passwordEncoder.matches("senha-nova-123", "old-hash")).thenReturn(false);
        when(passwordEncoder.encode("senha-nova-123")).thenReturn("new-hash");

        service.changePassword(USER_ID, new ChangePasswordDTO("senha-antiga", "senha-nova-123"));

        assertEquals("new-hash", existing.getPassword());
        verify(repository).save(existing);
    }

    @Test
    void refusesAWrongCurrentPasswordWithoutLoggingTheUserOut() {
        when(repository.findById(USER_ID)).thenReturn(Optional.of(user("old-hash")));
        when(passwordEncoder.matches("errada", "old-hash")).thenReturn(false);

        assertThrows(BadRequestException.class, () -> service.changePassword(USER_ID, new ChangePasswordDTO("errada", "senha-nova-123")));
        verify(repository, never()).save(any());
    }

    @Test
    void refusesShortMissingOrUnchangedNewPasswords() {
        assertThrows(BadRequestException.class, () -> service.changePassword(USER_ID, new ChangePasswordDTO("x", "curta")));
        assertThrows(BadRequestException.class, () -> service.changePassword(USER_ID, new ChangePasswordDTO(null, "senha-nova-123")));
        assertThrows(BadRequestException.class, () -> service.changePassword(USER_ID, new ChangePasswordDTO("x", null)));

        when(repository.findById(USER_ID)).thenReturn(Optional.of(user("old-hash")));
        when(passwordEncoder.matches("mesma-senha-1", "old-hash")).thenReturn(true);
        assertThrows(BadRequestException.class, () -> service.changePassword(USER_ID, new ChangePasswordDTO("mesma-senha-1", "mesma-senha-1")));
        verify(repository, never()).save(any());
    }
}
