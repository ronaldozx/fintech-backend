package com.globo.fintech_backend.Transactions.service;

import com.globo.fintech_backend.Auth.entity.User;
import com.globo.fintech_backend.Auth.repository.UserRepository;
import com.globo.fintech_backend.Transactions.dto.TransactionCreateDTO;
import com.globo.fintech_backend.Transactions.dto.TransactionRowDTO;
import com.globo.fintech_backend.Transactions.dto.TransactionUpdateDTO;
import com.globo.fintech_backend.Transactions.entity.Transaction;
import com.globo.fintech_backend.Transactions.enums.TransactionType;
import com.globo.fintech_backend.Transactions.repository.TransactionRepository;
import com.globo.fintech_backend.exception.BadRequestException;
import com.globo.fintech_backend.exception.ConflictException;
import com.globo.fintech_backend.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionEditServiceTest {

    private static final Long USER_ID = 7L;
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 21);

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private UserRepository userRepository;

    private TransactionEditService service;

    @BeforeEach
    void setUp() {
        service = new TransactionEditService(transactionRepository, userRepository);
    }

    private void saving() {
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(call -> call.getArgument(0));
    }

    private static Transaction imported(String amount, String category) {
        Transaction transaction = new Transaction();
        transaction.setId(5L);
        transaction.setDescription("PIX RECEBIDO");
        transaction.setAmount(new BigDecimal(amount));
        transaction.setType(new BigDecimal(amount).signum() < 0 ? TransactionType.EXPENSE : TransactionType.INCOME);
        transaction.setDate(LocalDate.of(2026, 9, 1));
        transaction.setCategory(category);
        transaction.setNeutral(false);
        transaction.setExternalId("ext-1");
        return transaction;
    }

    private static Transaction manual(String amount) {
        Transaction transaction = imported(amount, "Mercado");
        transaction.setExternalId(null);
        transaction.setManual(true);
        return transaction;
    }

    @Test
    void createsAManualExpenseWithANegativeAmount() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(new User()));
        saving();

        TransactionRowDTO row = service.create(USER_ID,
                new TransactionCreateDTO("  Feira  ", new BigDecimal("45.5"), TransactionType.EXPENSE, TODAY, " Mercado "), TODAY);

        assertEquals("Feira", row.description());
        assertEquals(new BigDecimal("-45.50"), row.amount());
        assertEquals("Mercado", row.category());
        assertTrue(row.manual());
        assertFalse(row.neutral());
    }

    @Test
    void createsAManualIncomeWithAPositiveAmountAndNoCategory() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(new User()));
        saving();

        TransactionRowDTO row = service.create(USER_ID,
                new TransactionCreateDTO("Venda", new BigDecimal("-100"), TransactionType.INCOME, TODAY, "  "), TODAY);

        assertEquals(new BigDecimal("100.00"), row.amount());
        assertNull(row.category());
    }

    @Test
    void refusesInvalidNewTransactions() {
        assertThrows(BadRequestException.class, () -> service.create(USER_ID,
                new TransactionCreateDTO("", new BigDecimal("10"), TransactionType.EXPENSE, TODAY, null), TODAY));
        assertThrows(BadRequestException.class, () -> service.create(USER_ID,
                new TransactionCreateDTO("Algo", BigDecimal.ZERO, TransactionType.EXPENSE, TODAY, null), TODAY));
        assertThrows(BadRequestException.class, () -> service.create(USER_ID,
                new TransactionCreateDTO("Algo", null, TransactionType.EXPENSE, TODAY, null), TODAY));
        assertThrows(BadRequestException.class, () -> service.create(USER_ID,
                new TransactionCreateDTO("Algo", new BigDecimal("10"), null, TODAY, null), TODAY));
        assertThrows(BadRequestException.class, () -> service.create(USER_ID,
                new TransactionCreateDTO("Algo", new BigDecimal("10"), TransactionType.EXPENSE, null, null), TODAY));
        assertThrows(BadRequestException.class, () -> service.create(USER_ID,
                new TransactionCreateDTO("Algo", new BigDecimal("10"), TransactionType.EXPENSE, LocalDate.of(1990, 1, 1), null), TODAY));
        assertThrows(BadRequestException.class, () -> service.create(USER_ID,
                new TransactionCreateDTO("Algo", new BigDecimal("10"), TransactionType.EXPENSE, TODAY.plusYears(2), null), TODAY));
        assertThrows(BadRequestException.class, () -> service.create(USER_ID,
                new TransactionCreateDTO("x".repeat(TransactionEditService.MAX_DESCRIPTION + 1), new BigDecimal("10"),
                        TransactionType.EXPENSE, TODAY, null), TODAY));
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void anImportedTransactionCanChangeItsCategoryAndWhetherItCounts() {
        Transaction existing = imported("-80.00", "Transferências");
        when(transactionRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(existing));
        saving();

        TransactionRowDTO row = service.update(USER_ID, 5L, new TransactionUpdateDTO("Presente", true, null, null, null, null), TODAY);

        assertEquals("Presente", row.category());
        assertTrue(row.neutral());
        assertEquals(TransactionEditService.USER_REASON, row.neutralReason());
        assertTrue(row.userEdited());
        assertEquals(new BigDecimal("-80.00"), row.amount());
    }

    @Test
    void makingATransactionCountAgainClearsTheReason() {
        Transaction existing = imported("-80.00", "Transferências");
        existing.setNeutral(true);
        existing.setNeutralReason("OWN_TRANSFER");
        when(transactionRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(existing));
        saving();

        TransactionRowDTO row = service.update(USER_ID, 5L, new TransactionUpdateDTO(null, false, null, null, null, null), TODAY);

        assertFalse(row.neutral());
        assertNull(row.neutralReason());
        assertTrue(row.userEdited());
    }

    @Test
    void anImportedTransactionCannotChangeAmountDescriptionDateOrType() {
        Transaction existing = imported("-80.00", "Mercado");
        when(transactionRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(existing));

        assertThrows(BadRequestException.class,
                () -> service.update(USER_ID, 5L, new TransactionUpdateDTO(null, null, null, new BigDecimal("10"), null, null), TODAY));
        assertThrows(BadRequestException.class,
                () -> service.update(USER_ID, 5L, new TransactionUpdateDTO(null, null, "Outro", null, null, null), TODAY));
        assertThrows(BadRequestException.class,
                () -> service.update(USER_ID, 5L, new TransactionUpdateDTO(null, null, null, null, TransactionType.INCOME, null), TODAY));
        assertThrows(BadRequestException.class,
                () -> service.update(USER_ID, 5L, new TransactionUpdateDTO(null, null, null, null, null, TODAY), TODAY));
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void aManualTransactionCanChangeEverythingAndKeepsTheSignRule() {
        Transaction existing = manual("-30.00");
        when(transactionRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(existing));
        saving();

        TransactionRowDTO row = service.update(USER_ID, 5L,
                new TransactionUpdateDTO("Lazer", null, "Show", new BigDecimal("120"), TransactionType.INCOME, LocalDate.of(2026, 9, 10)), TODAY);

        assertEquals("Show", row.description());
        assertEquals(new BigDecimal("120.00"), row.amount());
        assertEquals(TransactionType.INCOME, row.type());
        assertEquals(LocalDate.of(2026, 9, 10), row.date());
    }

    @Test
    void changingOnlyTheTypeOfAManualTransactionFlipsItsSign() {
        Transaction existing = manual("-30.00");
        when(transactionRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(existing));
        saving();

        TransactionRowDTO row = service.update(USER_ID, 5L, new TransactionUpdateDTO(null, null, null, null, TransactionType.INCOME, null), TODAY);

        assertEquals(new BigDecimal("30.00"), row.amount());
    }

    @Test
    void rejectsABlankCategoryOnUpdate() {
        Transaction existing = imported("-80.00", "Mercado");
        when(transactionRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(existing));

        assertThrows(BadRequestException.class,
                () -> service.update(USER_ID, 5L, new TransactionUpdateDTO("   ", null, null, null, null, null), TODAY));
    }

    @Test
    void cannotTouchAnotherUsersTransaction() {
        when(transactionRepository.findByIdAndUserId(99L, USER_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.update(USER_ID, 99L, new TransactionUpdateDTO("X", null, null, null, null, null), TODAY));
        assertThrows(ResourceNotFoundException.class, () -> service.delete(USER_ID, 99L));
    }

    @Test
    void deletesOnlyManualTransactions() {
        Transaction manualOne = manual("-30.00");
        Transaction importedOne = imported("-80.00", "Mercado");
        when(transactionRepository.findByIdAndUserId(5L, USER_ID)).thenReturn(Optional.of(manualOne));
        when(transactionRepository.findByIdAndUserId(6L, USER_ID)).thenReturn(Optional.of(importedOne));

        service.delete(USER_ID, 5L);
        verify(transactionRepository).delete(manualOne);

        assertThrows(ConflictException.class, () -> service.delete(USER_ID, 6L));
        verify(transactionRepository, never()).delete(importedOne);
    }
}
