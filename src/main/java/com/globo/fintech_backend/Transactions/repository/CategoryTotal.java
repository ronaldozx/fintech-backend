package com.globo.fintech_backend.Transactions.repository;

import java.math.BigDecimal;

public interface CategoryTotal {
    String getCategory();
    BigDecimal getTotal();
    Long getTransactionCount();
}
