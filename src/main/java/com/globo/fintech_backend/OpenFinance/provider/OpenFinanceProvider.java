package com.globo.fintech_backend.OpenFinance.provider;

import java.time.LocalDate;
import java.util.List;

public interface OpenFinanceProvider {

    String createConnectToken(Long userId);

    List<ProviderAccount> listAccounts(String itemId);

    List<ProviderTransaction> listTransactions(String accountId, LocalDate from, LocalDate to);
}
