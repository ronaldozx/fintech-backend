package com.globo.fintech_backend.OpenFinance.accounts;

import com.globo.fintech_backend.OpenFinance.connection.BankConnection;
import com.globo.fintech_backend.OpenFinance.connection.BankConnectionRepository;
import com.globo.fintech_backend.OpenFinance.exception.OpenFinanceException;
import com.globo.fintech_backend.OpenFinance.provider.OpenFinanceProvider;
import com.globo.fintech_backend.OpenFinance.provider.ProviderAccount;
import com.globo.fintech_backend.OpenFinance.provider.ProviderAccountType;
import com.globo.fintech_backend.Transactions.enums.PaymentMethod;
import com.globo.fintech_backend.Transactions.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class AccountsService {

    private final BankConnectionRepository connectionRepository;
    private final TransactionRepository transactionRepository;
    private final OpenFinanceProvider provider;

    public AccountsService(BankConnectionRepository connectionRepository,
                           TransactionRepository transactionRepository,
                           OpenFinanceProvider provider) {
        this.connectionRepository = connectionRepository;
        this.transactionRepository = transactionRepository;
        this.provider = provider;
    }

    public AccountsOverviewDTO overview(Long userId, LocalDate today) {
        List<AccountDTO> accounts = new ArrayList<>();
        List<String> unavailable = new ArrayList<>();

        for (BankConnection connection : connectionRepository.findByUserIdOrderByCreatedAtDesc(userId)) {
            try {
                accounts.addAll(toDtos(connection, provider.listAccounts(connection.getItemId())));
            } catch (OpenFinanceException e) {
                unavailable.add(connection.getInstitutionName() == null ? "Conexão " + connection.getId() : connection.getInstitutionName());
            }
        }

        BigDecimal bank = sum(accounts, ProviderAccountType.BANK.name());
        BigDecimal cards = sum(accounts, ProviderAccountType.CREDIT.name());
        BigDecimal cardSpending = transactionRepository
                .sumExpensesByPaymentMethod(userId, PaymentMethod.CREDIT, today.withDayOfMonth(1), today)
                .abs();

        return new AccountsOverviewDTO(accounts, bank, cards, bank.subtract(cards), cardSpending, unavailable);
    }

    private static List<AccountDTO> toDtos(BankConnection connection, List<ProviderAccount> provided) {
        String institution = AccountNaming.institution(provided, connection.getInstitutionName());

        return provided.stream()
                .map(account -> new AccountDTO(
                        connection.getId(),
                        institution,
                        AccountNaming.accountName(account),
                        account.type().name(),
                        AccountNaming.masked(account.number()),
                        account.balance() == null ? BigDecimal.ZERO : account.balance(),
                        account.currencyCode(),
                        account.creditLimit(),
                        account.availableCredit(),
                        account.dueDate(),
                        account.overdraftLimit(),
                        account.overdraftUsed()
                ))
                .toList();
    }

    private static BigDecimal sum(List<AccountDTO> accounts, String type) {
        return accounts.stream()
                .filter(account -> account.type().equals(type))
                .map(AccountDTO::balance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
