package com.globo.fintech_backend.Transactions.service;

import com.globo.fintech_backend.Transactions.entity.Transaction;
import com.globo.fintech_backend.Transactions.enums.TransactionType;
import com.globo.fintech_backend.Transactions.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OwnTransferReconcilerTest {

    private static final Long USER_ID = 7L;

    @Mock
    private TransactionRepository transactionRepository;

    private static Transaction transfer(long id, String amount, String date, String category) {
        Transaction transaction = new Transaction();
        transaction.setId(id);
        transaction.setAmount(new BigDecimal(amount));
        transaction.setType(new BigDecimal(amount).signum() < 0 ? TransactionType.EXPENSE : TransactionType.INCOME);
        transaction.setDate(LocalDate.parse(date));
        transaction.setCategory(category);
        transaction.setNeutral(false);
        return transaction;
    }

    @Test
    void pairsAnExpenseWithTheMatchingIncomeOfTheSameDay() {
        List<Transaction[]> pairs = OwnTransferReconciler.match(List.of(
                transfer(1, "-500.00", "2026-09-10", "Transferência - PIX"),
                transfer(2, "500.00", "2026-09-10", "Transferência - PIX")));

        assertEquals(1, pairs.size());
        assertEquals(1L, pairs.get(0)[0].getId());
        assertEquals(2L, pairs.get(0)[1].getId());
    }

    @Test
    void toleratesOneDayBetweenTheTwoSides() {
        assertEquals(1, OwnTransferReconciler.match(List.of(
                transfer(1, "-500.00", "2026-09-10", "Transferências"),
                transfer(2, "500.00", "2026-09-11", "Transferências"))).size());

        assertTrue(OwnTransferReconciler.match(List.of(
                transfer(1, "-500.00", "2026-09-10", "Transferências"),
                transfer(2, "500.00", "2026-09-12", "Transferências"))).isEmpty());
    }

    @Test
    void amountsMustBeEqual() {
        assertTrue(OwnTransferReconciler.match(List.of(
                transfer(1, "-500.00", "2026-09-10", "Transferência - PIX"),
                transfer(2, "499.99", "2026-09-10", "Transferência - PIX"))).isEmpty());
    }

    @Test
    void eachSideIsUsedOnlyOnce() {
        List<Transaction[]> pairs = OwnTransferReconciler.match(List.of(
                transfer(1, "-100.00", "2026-09-10", "Transferência - PIX"),
                transfer(2, "-100.00", "2026-09-10", "Transferência - PIX"),
                transfer(3, "100.00", "2026-09-10", "Transferência - PIX")));

        assertEquals(1, pairs.size());
        assertEquals(3L, pairs.get(0)[1].getId());
    }

    @Test
    void prefersTheClosestIncomeInTime() {
        List<Transaction[]> pairs = OwnTransferReconciler.match(List.of(
                transfer(1, "-100.00", "2026-09-10", "Transferência - PIX"),
                transfer(2, "100.00", "2026-09-11", "Transferência - PIX"),
                transfer(3, "100.00", "2026-09-10", "Transferência - PIX")));

        assertEquals(3L, pairs.get(0)[1].getId());
    }

    @Test
    void ignoresThirdPartyTransfersAndOtherCategories() {
        assertTrue(OwnTransferReconciler.match(List.of(
                transfer(1, "-100.00", "2026-09-10", "Transferência para terceiros - PIX"),
                transfer(2, "100.00", "2026-09-10", "Transferência para terceiros - PIX"))).isEmpty());

        assertTrue(OwnTransferReconciler.match(List.of(
                transfer(1, "-100.00", "2026-09-10", "Mercado"),
                transfer(2, "100.00", "2026-09-10", "Mercado"))).isEmpty());

        assertTrue(OwnTransferReconciler.match(List.of(
                transfer(1, "-100.00", "2026-09-10", null),
                transfer(2, "100.00", "2026-09-10", null))).isEmpty());
    }

    @Test
    void anExpenseAloneStaysUntouched() {
        assertTrue(OwnTransferReconciler.match(List.of(transfer(1, "-100.00", "2026-09-10", "Transferência - PIX"))).isEmpty());
    }

    @Test
    void marksBothSidesAsNeutralWithTheReasonAndCountsThePairs() {
        Transaction expense = transfer(1, "-250.00", "2026-09-10", "Transferência - TED");
        Transaction income = transfer(2, "250.00", "2026-09-10", "Transferência - TED");
        Transaction lonely = transfer(3, "-40.00", "2026-09-12", "Transferência - PIX");
        when(transactionRepository.findTransferCandidates(USER_ID)).thenReturn(List.of(expense, income, lonely));

        int pairs = new OwnTransferReconciler(transactionRepository).reconcile(USER_ID);

        assertEquals(1, pairs);
        assertTrue(expense.getNeutral());
        assertTrue(income.getNeutral());
        assertEquals(OwnTransferReconciler.REASON, expense.getNeutralReason());
        assertEquals(OwnTransferReconciler.REASON, income.getNeutralReason());
        assertFalse(lonely.getNeutral());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Transaction>> saved = ArgumentCaptor.forClass(List.class);
        verify(transactionRepository).saveAll(saved.capture());
        assertEquals(2, saved.getValue().size());
    }

    @Test
    void savesNothingWhenThereIsNoPair() {
        when(transactionRepository.findTransferCandidates(USER_ID)).thenReturn(List.of());

        assertEquals(0, new OwnTransferReconciler(transactionRepository).reconcile(USER_ID));
        verify(transactionRepository).saveAll(anyList());
    }
}
