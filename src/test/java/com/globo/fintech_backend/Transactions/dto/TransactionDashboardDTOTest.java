package com.globo.fintech_backend.Transactions.dto;

import com.globo.fintech_backend.Transactions.repository.TransactionSummary;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TransactionDashboardDTOTest {

    private static final Page<TransactionDTO> NO_TRANSACTIONS = new PageImpl<>(List.of());

    private static TransactionSummary summary(BigDecimal income, BigDecimal expense) {
        return new TransactionSummary() {
            @Override
            public BigDecimal getTotalIncome() {
                return income;
            }

            @Override
            public BigDecimal getTotalExpense() {
                return expense;
            }
        };
    }

    @Test
    void negativeStoredExpensesAreReportedAsPositiveAndSubtractedFromBalance() {
        TransactionDashboardDTO dto = new TransactionDashboardDTO(
                summary(new BigDecimal("3000.00"), new BigDecimal("-150.50")),
                NO_TRANSACTIONS
        );

        assertEquals(new BigDecimal("150.50"), dto.getTotalExpense());
        assertEquals(new BigDecimal("2849.50"), dto.getNetBalance());
    }

    @Test
    void emptyPeriodYieldsZeros() {
        TransactionDashboardDTO dto = new TransactionDashboardDTO(summary(null, null), NO_TRANSACTIONS);

        assertEquals(BigDecimal.ZERO, dto.getTotalIncome());
        assertEquals(BigDecimal.ZERO, dto.getTotalExpense());
        assertEquals(BigDecimal.ZERO, dto.getNetBalance());
    }
}
