package com.globo.fintech_backend.Transactions.repository;

import java.math.BigDecimal;

public interface TransactionSummary {
    BigDecimal getTotalIncome();
    BigDecimal getTotalExpense();
}