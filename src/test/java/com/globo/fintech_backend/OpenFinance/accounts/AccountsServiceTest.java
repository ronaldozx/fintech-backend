package com.globo.fintech_backend.OpenFinance.accounts;

import com.globo.fintech_backend.OpenFinance.connection.BankConnection;
import com.globo.fintech_backend.OpenFinance.connection.BankConnectionRepository;
import com.globo.fintech_backend.OpenFinance.exception.OpenFinanceException;
import com.globo.fintech_backend.OpenFinance.provider.OpenFinanceProvider;
import com.globo.fintech_backend.OpenFinance.provider.ProviderAccount;
import com.globo.fintech_backend.OpenFinance.provider.ProviderAccountType;
import com.globo.fintech_backend.Transactions.enums.PaymentMethod;
import com.globo.fintech_backend.Transactions.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountsServiceTest {

    private static final Long USER_ID = 7L;
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 21);

    @Mock
    private BankConnectionRepository connectionRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private OpenFinanceProvider provider;

    private AccountsService service;

    @BeforeEach
    void setUp() {
        service = new AccountsService(connectionRepository, transactionRepository, provider);
    }

    private static BankConnection connection(long id, String item, String name) {
        BankConnection connection = new BankConnection();
        connection.setId(id);
        connection.setItemId(item);
        connection.setInstitutionName(name);
        return connection;
    }

    private static ProviderAccount bank(String name, String balance) {
        return new ProviderAccount("b-" + name, ProviderAccountType.BANK, name, new BigDecimal(balance), "BRL", "111222",
                null, null, null, null, null, new BigDecimal("500.00"), new BigDecimal("120.00"));
    }

    private static ProviderAccount card(String name, String due, String limit, String available) {
        return new ProviderAccount("c-" + name, ProviderAccountType.CREDIT, name, new BigDecimal(due), "BRL", "4321",
                null, new BigDecimal(limit), new BigDecimal(available), LocalDate.of(2026, 10, 5), "VISA", null, null);
    }

    @Test
    void groupsAccountsByBankAndComputesTheNetPosition() {
        when(connectionRepository.findByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of(
                connection(1, "item-1", "MeuPluggy"), connection(2, "item-2", "MeuPluggy")));
        when(provider.listAccounts("item-1")).thenReturn(List.of(bank("Banco Santander", "-1489.53"), card("FREE VISA", "1117.10", "2490", "1372.90")));
        when(provider.listAccounts("item-2")).thenReturn(List.of(
                bank("Nu Pagamentos S.A. - Instituição de Pagamento", "2.93"), card("gold", "3593.03", "5000", "0")));
        when(transactionRepository.sumExpensesByPaymentMethod(USER_ID, PaymentMethod.CREDIT, LocalDate.of(2026, 9, 1), TODAY))
                .thenReturn(new BigDecimal("-845.50"));

        AccountsOverviewDTO overview = service.overview(USER_ID, TODAY);

        assertEquals(4, overview.accounts().size());
        assertEquals("Banco Santander", overview.accounts().get(0).institution());
        assertEquals("Banco Santander", overview.accounts().get(1).institution());
        assertEquals("Nubank", overview.accounts().get(2).institution());
        assertEquals("Nubank", overview.accounts().get(3).institution());
        assertEquals(new BigDecimal("-1486.60"), overview.bankBalance());
        assertEquals(new BigDecimal("4710.13"), overview.cardBalanceDue());
        assertEquals(new BigDecimal("-6196.73"), overview.netPosition());
        assertEquals(new BigDecimal("845.50"), overview.cardSpendingThisMonth());
        assertTrue(overview.unavailableConnections().isEmpty());
    }

    @Test
    void exposesCardLimitsDueDatesAndOverdraft() {
        when(connectionRepository.findByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of(connection(1, "item-1", "MeuPluggy")));
        when(provider.listAccounts("item-1")).thenReturn(List.of(bank("Banco Santander", "10"), card("gold", "100", "5000", "0")));
        when(transactionRepository.sumExpensesByPaymentMethod(USER_ID, PaymentMethod.CREDIT, LocalDate.of(2026, 9, 1), TODAY))
                .thenReturn(BigDecimal.ZERO);

        List<AccountDTO> accounts = service.overview(USER_ID, TODAY).accounts();

        AccountDTO checking = accounts.get(0);
        assertEquals("Conta corrente", checking.name());
        assertEquals("•••• 1222", checking.maskedNumber());
        assertEquals(new BigDecimal("500.00"), checking.overdraftLimit());
        assertEquals(new BigDecimal("120.00"), checking.overdraftUsed());

        AccountDTO card = accounts.get(1);
        assertEquals("CREDIT", card.type());
        assertEquals(new BigDecimal("5000"), card.creditLimit());
        assertEquals(new BigDecimal("0"), card.availableCredit());
        assertEquals(LocalDate.of(2026, 10, 5), card.dueDate());
    }

    @Test
    void oneFailingConnectionDoesNotHideTheOthers() {
        when(connectionRepository.findByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of(
                connection(1, "item-1", "Banco Quebrado"), connection(2, "item-2", "MeuPluggy")));
        when(provider.listAccounts("item-1")).thenThrow(new OpenFinanceException("fora do ar"));
        when(provider.listAccounts("item-2")).thenReturn(List.of(bank("Banco Santander", "100")));
        when(transactionRepository.sumExpensesByPaymentMethod(USER_ID, PaymentMethod.CREDIT, LocalDate.of(2026, 9, 1), TODAY))
                .thenReturn(BigDecimal.ZERO);

        AccountsOverviewDTO overview = service.overview(USER_ID, TODAY);

        assertEquals(1, overview.accounts().size());
        assertEquals(List.of("Banco Quebrado"), overview.unavailableConnections());
    }

    @Test
    void withoutConnectionsEverythingIsZero() {
        when(connectionRepository.findByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of());
        when(transactionRepository.sumExpensesByPaymentMethod(USER_ID, PaymentMethod.CREDIT, LocalDate.of(2026, 9, 1), TODAY))
                .thenReturn(BigDecimal.ZERO);

        AccountsOverviewDTO overview = service.overview(USER_ID, TODAY);

        assertTrue(overview.accounts().isEmpty());
        assertEquals(BigDecimal.ZERO, overview.netPosition());
    }
}
