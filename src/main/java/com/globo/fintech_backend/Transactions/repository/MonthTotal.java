package com.globo.fintech_backend.Transactions.repository;

import java.math.BigDecimal;

public interface MonthTotal {
    Integer getPeriodYear();
    Integer getPeriodMonth();
    BigDecimal getIncome();
    BigDecimal getExpense();
}
