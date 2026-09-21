package com.globo.fintech_backend.OpenFinance.accounts;

import java.math.BigDecimal;
import java.util.List;

public record AccountsOverviewDTO(
        List<AccountDTO> accounts,
        BigDecimal bankBalance,
        BigDecimal cardBalanceDue,
        BigDecimal netPosition,
        BigDecimal cardSpendingThisMonth,
        List<String> unavailableConnections
) {}
