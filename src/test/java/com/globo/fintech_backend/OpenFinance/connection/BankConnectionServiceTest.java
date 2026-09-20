package com.globo.fintech_backend.OpenFinance.connection;

import com.globo.fintech_backend.Auth.entity.User;
import com.globo.fintech_backend.Auth.repository.UserRepository;
import com.globo.fintech_backend.OpenFinance.connection.dto.BankConnectionDTO;
import com.globo.fintech_backend.OpenFinance.provider.OpenFinanceProvider;
import com.globo.fintech_backend.OpenFinance.provider.ProviderItem;
import com.globo.fintech_backend.exception.BadRequestException;
import com.globo.fintech_backend.exception.ConflictException;
import com.globo.fintech_backend.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BankConnectionServiceTest {

    private static final Long USER_ID = 7L;
    private static final Long OTHER_USER_ID = 8L;
    private static final String ITEM_ID = "item-1";

    @Mock
    private BankConnectionRepository repository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OpenFinanceProvider provider;

    private BankConnectionService service;

    @BeforeEach
    void setUp() {
        service = new BankConnectionService(repository, userRepository, provider);
    }

    private static User user(Long id) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private static BankConnection connection(Long id, User owner) {
        BankConnection connection = new BankConnection();
        connection.setId(id);
        connection.setUser(owner);
        connection.setItemId(ITEM_ID);
        connection.setInstitutionName("Banco Teste");
        connection.setStatus("UPDATED");
        return connection;
    }

    @Test
    void registersAnItemThatBelongsToTheLoggedUser() {
        when(repository.findByItemId(ITEM_ID)).thenReturn(Optional.empty());
        when(provider.getItem(ITEM_ID)).thenReturn(new ProviderItem(ITEM_ID, "UPDATED", "Banco Teste", "7"));
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user(USER_ID)));
        when(repository.save(any(BankConnection.class))).thenAnswer(call -> call.getArgument(0));

        BankConnectionDTO dto = service.register(USER_ID, ITEM_ID);

        assertEquals(ITEM_ID, dto.itemId());
        assertEquals("Banco Teste", dto.institutionName());
        assertEquals("UPDATED", dto.status());
    }

    @Test
    void rejectsAnItemOwnedByAnotherClientUser() {
        when(repository.findByItemId(ITEM_ID)).thenReturn(Optional.empty());
        when(provider.getItem(ITEM_ID)).thenReturn(new ProviderItem(ITEM_ID, "UPDATED", "Banco Teste", "99"));

        assertThrows(ResourceNotFoundException.class, () -> service.register(USER_ID, ITEM_ID));
        verify(repository, never()).save(any());
    }

    @Test
    void registeringAnAlreadyLinkedItemForTheSameUserIsIdempotent() {
        when(repository.findByItemId(ITEM_ID)).thenReturn(Optional.of(connection(1L, user(USER_ID))));

        BankConnectionDTO dto = service.register(USER_ID, ITEM_ID);

        assertEquals(1L, dto.id());
        verify(provider, never()).getItem(any());
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsAnItemAlreadyLinkedToAnotherUser() {
        when(repository.findByItemId(ITEM_ID)).thenReturn(Optional.of(connection(1L, user(OTHER_USER_ID))));

        assertThrows(ConflictException.class, () -> service.register(USER_ID, ITEM_ID));
        verify(provider, never()).getItem(any());
    }

    @Test
    void rejectsBlankItemId() {
        assertThrows(BadRequestException.class, () -> service.register(USER_ID, " "));
        assertThrows(BadRequestException.class, () -> service.register(USER_ID, null));
    }

    @Test
    void deletingRevokesAtTheProviderThenRemovesLocally() {
        BankConnection connection = connection(1L, user(USER_ID));
        when(repository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(connection));

        service.delete(USER_ID, 1L);

        verify(provider).deleteItem(ITEM_ID);
        verify(repository).delete(connection);
    }

    @Test
    void cannotDeleteAConnectionOfAnotherUser() {
        when(repository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.delete(USER_ID, 1L));
        verify(provider, never()).deleteItem(any());
        verify(repository, never()).delete(any());
    }

    @Test
    void keepsTheLocalRecordWhenTheProviderFailsToDelete() {
        BankConnection connection = connection(1L, user(USER_ID));
        when(repository.findByIdAndUserId(1L, USER_ID)).thenReturn(Optional.of(connection));
        org.mockito.Mockito.doThrow(new RuntimeException("provider down")).when(provider).deleteItem(ITEM_ID);

        assertThrows(RuntimeException.class, () -> service.delete(USER_ID, 1L));
        verify(repository, never()).delete(any());
    }

    @Test
    void listsOnlyTheConnectionsOfTheUser() {
        when(repository.findByUserIdOrderByCreatedAtDesc(USER_ID))
                .thenReturn(List.of(connection(1L, user(USER_ID))));

        assertEquals(1, service.list(USER_ID).size());
    }
}
