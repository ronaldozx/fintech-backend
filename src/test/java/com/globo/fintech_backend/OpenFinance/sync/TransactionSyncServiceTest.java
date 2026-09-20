package com.globo.fintech_backend.OpenFinance.sync;

import com.globo.fintech_backend.Auth.entity.User;
import com.globo.fintech_backend.OpenFinance.connection.BankConnection;
import com.globo.fintech_backend.OpenFinance.connection.BankConnectionRepository;
import com.globo.fintech_backend.OpenFinance.provider.OpenFinanceProvider;
import com.globo.fintech_backend.OpenFinance.provider.ProviderAccount;
import com.globo.fintech_backend.OpenFinance.provider.ProviderAccountType;
import com.globo.fintech_backend.OpenFinance.provider.ProviderTransaction;
import com.globo.fintech_backend.OpenFinance.provider.ProviderTransactionType;
import com.globo.fintech_backend.Transactions.entity.Transaction;
import com.globo.fintech_backend.Transactions.enums.PaymentMethod;
import com.globo.fintech_backend.Transactions.enums.TransactionType;
import com.globo.fintech_backend.Transactions.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionSyncServiceTest {

    private static final Long USER_ID = 7L;
    private static final String ITEM_ID = "item-1";
    private static final LocalDate TODAY = LocalDate.of(2026, 6, 15);

    @Mock
    private BankConnectionRepository connectionRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private OpenFinanceProvider provider;

    @Captor
    private ArgumentCaptor<List<Transaction>> savedCaptor;

    private TransactionSyncService service;
    private BankConnection connection;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-06-15T12:00:00Z"), ZoneOffset.UTC);
        service = new TransactionSyncService(
                connectionRepository, transactionRepository, provider, new CategoryClassifier(), clock);

        User user = new User();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        connection = new BankConnection();
        connection.setUser(user);
        connection.setItemId(ITEM_ID);
    }

    private static ProviderAccount bankAccount() {
        return new ProviderAccount("acc-bank", ProviderAccountType.BANK, "Conta", BigDecimal.TEN, "BRL");
    }

    private static ProviderAccount cardAccount() {
        return new ProviderAccount("acc-card", ProviderAccountType.CREDIT, "Cartão", BigDecimal.TEN, "BRL");
    }

    private static ProviderTransaction tx(String id, String description, String amount,
                                          ProviderTransactionType type, boolean posted, String category) {
        return new ProviderTransaction(id, description, new BigDecimal(amount), LocalDate.of(2026, 6, 10),
                type, posted, category);
    }

    private List<Transaction> importedFor(ProviderAccount account, List<ProviderTransaction> provided, Set<String> existing) {
        when(provider.listAccounts(ITEM_ID)).thenReturn(List.of(account));
        when(provider.listTransactions(eq(account.id()), any(), any())).thenReturn(provided);
        when(transactionRepository.findExistingExternalIds(eq(USER_ID), anyCollection())).thenReturn(existing);

        service.syncConnection(connection);

        verify(transactionRepository).saveAll(savedCaptor.capture());
        return savedCaptor.getValue();
    }

    @Test
    void storesDebitsAsNegativeExpensesAndCreditsAsPositiveIncome() {
        List<Transaction> saved = importedFor(bankAccount(), List.of(
                tx("t-1", "Mercado Extra", "150.50", ProviderTransactionType.DEBIT, true, null),
                tx("t-2", "Salario", "3000.00", ProviderTransactionType.CREDIT, true, null)
        ), Set.of());

        Transaction expense = saved.get(0);
        assertEquals(new BigDecimal("-150.50"), expense.getAmount());
        assertEquals(TransactionType.EXPENSE, expense.getType());
        assertEquals(PaymentMethod.DEBIT, expense.getPaymentMethod());
        assertEquals("t-1", expense.getExternalId());
        assertEquals("Alimentação", expense.getCategory());

        Transaction income = saved.get(1);
        assertEquals(new BigDecimal("3000.00"), income.getAmount());
        assertEquals(TransactionType.INCOME, income.getType());
    }

    @Test
    void doesNotDoubleNegateAmountsThatAlreadyComeSigned() {
        List<Transaction> saved = importedFor(bankAccount(), List.of(
                tx("t-1", "Mercado", "-80.00", ProviderTransactionType.DEBIT, true, null)
        ), Set.of());

        assertEquals(new BigDecimal("-80.00"), saved.get(0).getAmount());
    }

    @Test
    void skipsTransactionsAlreadyImported() {
        List<Transaction> saved = importedFor(bankAccount(), List.of(
                tx("t-1", "Mercado", "10.00", ProviderTransactionType.DEBIT, true, null),
                tx("t-2", "Padaria", "5.00", ProviderTransactionType.DEBIT, true, null)
        ), Set.of("t-1"));

        assertEquals(1, saved.size());
        assertEquals("t-2", saved.get(0).getExternalId());
    }

    @Test
    void ignoresPendingTransactions() {
        List<Transaction> saved = importedFor(bankAccount(), List.of(
                tx("t-1", "Mercado", "10.00", ProviderTransactionType.DEBIT, false, null),
                tx("t-2", "Padaria", "5.00", ProviderTransactionType.DEBIT, true, null)
        ), Set.of());

        assertEquals(1, saved.size());
        assertEquals("t-2", saved.get(0).getExternalId());
    }

    @Test
    void cardChargesAreImportedAsCreditButCardPaymentsAreIgnored() {
        List<Transaction> saved = importedFor(cardAccount(), List.of(
                tx("c-1", "Amazon", "200.00", ProviderTransactionType.DEBIT, true, null),
                tx("c-2", "Pagamento recebido", "500.00", ProviderTransactionType.CREDIT, true, null)
        ), Set.of());

        assertEquals(1, saved.size());
        assertEquals("c-1", saved.get(0).getExternalId());
        assertEquals(PaymentMethod.CREDIT, saved.get(0).getPaymentMethod());
        assertEquals(new BigDecimal("-200.00"), saved.get(0).getAmount());
    }

    @Test
    void prefersTheProviderCategoryOverTheClassifier() {
        List<Transaction> saved = importedFor(bankAccount(), List.of(
                tx("t-1", "Mercado Extra", "10.00", ProviderTransactionType.DEBIT, true, "Health")
        ), Set.of());

        assertEquals("Health", saved.get(0).getCategory());
    }

    @Test
    void firstSyncFetchesTwelveMonthsOfHistory() {
        when(provider.listAccounts(ITEM_ID)).thenReturn(List.of(bankAccount()));
        when(provider.listTransactions(any(), any(), any())).thenReturn(List.of());

        service.syncConnection(connection);

        verify(provider).listTransactions("acc-bank", LocalDate.of(2025, 6, 15), TODAY);
    }

    @Test
    void laterSyncsStartAWeekBeforeTheLastSync() {
        connection.setLastSyncedAt(LocalDateTime.of(2026, 6, 10, 8, 0));
        when(provider.listAccounts(ITEM_ID)).thenReturn(List.of(bankAccount()));
        when(provider.listTransactions(any(), any(), any())).thenReturn(List.of());

        service.syncConnection(connection);

        verify(provider).listTransactions("acc-bank", LocalDate.of(2026, 6, 3), TODAY);
    }

    @Test
    void recordsTheSyncTimeAndSkipsSavingWhenNothingIsNew() {
        when(provider.listAccounts(ITEM_ID)).thenReturn(List.of(bankAccount()));
        when(provider.listTransactions(any(), any(), any())).thenReturn(List.of());

        int imported = service.syncConnection(connection);

        assertEquals(0, imported);
        assertNotNull(connection.getLastSyncedAt());
        verify(connectionRepository).save(connection);
        verify(transactionRepository, never()).saveAll(any());
    }

    @Test
    void syncUserCountsEveryConnection() {
        when(connectionRepository.findByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of(connection));
        when(provider.listAccounts(ITEM_ID)).thenReturn(List.of(bankAccount()));
        when(provider.listTransactions(any(), any(), any())).thenReturn(List.of(
                tx("t-1", "Mercado", "10.00", ProviderTransactionType.DEBIT, true, null)
        ));
        when(transactionRepository.findExistingExternalIds(eq(USER_ID), anyCollection())).thenReturn(Set.of());

        SyncResultDTO result = service.syncUser(USER_ID);

        assertEquals(1, result.connections());
        assertEquals(1, result.imported());
    }
}
