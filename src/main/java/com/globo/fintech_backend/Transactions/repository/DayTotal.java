package com.globo.fintech_backend.Transactions.repository;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface DayTotal {
    LocalDate getPeriodDate();
    BigDecimal getIncome();
    BigDecimal getExpense();
}
