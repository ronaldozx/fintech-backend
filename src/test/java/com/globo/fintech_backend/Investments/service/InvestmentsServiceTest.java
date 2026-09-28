package com.globo.fintech_backend.Investments.service;

import com.globo.fintech_backend.Investments.dto.AllocationDTO;
import com.globo.fintech_backend.Investments.dto.InvestmentDTO;
import com.globo.fintech_backend.Investments.dto.InvestmentsOverviewDTO;
import com.globo.fintech_backend.OpenFinance.accounts.AccountsOverviewDTO;
import com.globo.fintech_backend.OpenFinance.accounts.AccountsService;
import com.globo.fintech_backend.OpenFinance.connection.BankConnection;
import com.globo.fintech_backend.OpenFinance.connection.BankConnectionRepository;
import com.globo.fintech_backend.OpenFinance.provider.OpenFinanceProvider;
import com.globo.fintech_backend.OpenFinance.provider.ProviderInvestment;
import com.globo.fintech_backend.Transactions.repository.MonthTotal;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvestmentsServiceTest {

    private static final Long USER_ID = 7L;
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 21);

    @Mock
    private BankConnectionRepository connectionRepository;

    @Mock
    private OpenFinanceProvider provider;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountsService accountsService;

    private InvestmentsService service;

    @BeforeEach
    void setUp() {
        service = new InvestmentsService(connectionRepository, provider, transactionRepository, accountsService);
    }

    private static BankConnection connection(String itemId, String name) {
        BankConnection connection = new BankConnection();
        connection.setItemId(itemId);
        connection.setInstitutionName(name);
        return connection;
    }

    private static ProviderInvestment investment(String id, String type, String balance, String invested, LocalDate due, String issuer) {
        return new ProviderInvestment(id, "Investimento " + id, type, "CDB", new BigDecimal(balance),
                invested == null ? null : new BigDecimal(invested), null, due, TODAY.minusYears(1), issuer, new BigDecimal("100"), "CDI");
    }

    private static MonthTotal expenses(String expense) {
        MonthTotal total = mock(MonthTotal.class);
        when(total.getExpense()).thenReturn(new BigDecimal(expense).negate());
        return total;
    }

    private void world(List<ProviderInvestment> investments, String bankBalance, MonthTotal... months) {
        when(connectionRepository.findByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of(connection("item-1", "Nubank")));
        when(provider.listInvestments("item-1")).thenReturn(investments);
        when(transactionRepository.getMonthlyTotals(eq(USER_ID), any(), any())).thenReturn(List.of(months));
        if (bankBalance == null) {
            when(accountsService.overview(USER_ID, TODAY)).thenThrow(new IllegalStateException("Pluggy fora do ar"));
        } else {
            when(accountsService.overview(USER_ID, TODAY)).thenReturn(new AccountsOverviewDTO(
                    List.of(), new BigDecimal(bankBalance), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of()));
        }
    }

    @Test
    void totalsTheHoldingsAndComputesTheGainOnlyWhereTheCostIsKnown() {
        world(List.of(
                        investment("a", "FIXED_INCOME", "1100.00", "1000.00", null, "Banco X"),
                        investment("b", "FIXED_INCOME", "500.00", null, null, "Banco X")),
                "0");

        InvestmentsOverviewDTO overview = service.overview(USER_ID, TODAY);

        assertEquals(0, new BigDecimal("1600.00").compareTo(overview.totalBalance()));
        assertEquals(0, new BigDecimal("1000.00").compareTo(overview.totalInvested()));
        assertEquals(0, new BigDecimal("100.00").compareTo(overview.totalProfit()));
        InvestmentDTO first = overview.investments().get(0);
        assertEquals("a", first.id());
        assertEquals(new BigDecimal("10.00"), first.profitPercent());
        assertNull(overview.investments().get(1).profit());
    }

    @Test
    void withoutAnyKnownCostThereIsNoTotalGain() {
        world(List.of(investment("a", "FIXED_INCOME", "500.00", null, null, "Banco X")), "0");

        InvestmentsOverviewDTO overview = service.overview(USER_ID, TODAY);

        assertNull(overview.totalInvested());
        assertNull(overview.totalProfit());
    }

    @Test
    void emptyOrZeroBalanceInvestmentsAreDropped() {
        world(List.of(investment("a", "FIXED_INCOME", "0", "100", null, "Banco X")), "0");

        assertTrue(service.overview(USER_ID, TODAY).investments().isEmpty());
    }

    @Test
    void allocatesByTypeWithPortugueseLabelsBiggestFirst() {
        world(List.of(
                        investment("a", "FIXED_INCOME", "600.00", null, null, "X"),
                        investment("b", "EQUITY", "300.00", null, null, "Y"),
                        investment("c", "OTHER", "100.00", null, null, "Z")),
                "0");

        List<AllocationDTO> allocation = service.overview(USER_ID, TODAY).allocation();

        assertEquals(List.of("Renda fixa", "Ações", "Outros"), allocation.stream().map(AllocationDTO::label).toList());
        assertEquals(List.of(60, 30, 10), allocation.stream().map(AllocationDTO::percent).toList());
    }

    @Test
    void listsMaturitiesWithinNinetyDaysInDateOrder() {
        world(List.of(
                        investment("late", "FIXED_INCOME", "100", null, TODAY.plusDays(91), "X"),
                        investment("soon", "FIXED_INCOME", "100", null, TODAY.plusDays(10), "X"),
                        investment("today", "FIXED_INCOME", "100", null, TODAY, "X"),
                        investment("past", "FIXED_INCOME", "100", null, TODAY.minusDays(1), "X"),
                        investment("none", "FIXED_INCOME", "100", null, null, "X")),
                "0");

        List<String> ids = service.overview(USER_ID, TODAY).upcomingMaturities().stream().map(InvestmentDTO::id).toList();

        assertEquals(List.of("today", "soon"), ids);
    }

    @Test
    void flagsWhenOneIssuerHoldsHalfOrMoreOfThePortfolio() {
        world(List.of(
                        investment("a", "FIXED_INCOME", "700", null, null, "Banco X"),
                        investment("b", "FIXED_INCOME", "300", null, null, "Banco Y")),
                "0");

        assertEquals("Banco X", service.overview(USER_ID, TODAY).concentration().name());
        assertEquals(70, service.overview(USER_ID, TODAY).concentration().percent());
    }

    @Test
    void noConcentrationNoteWhenSplitEvenly() {
        world(List.of(
                        investment("a", "FIXED_INCOME", "400", null, null, "Banco X"),
                        investment("b", "FIXED_INCOME", "300", null, null, "Banco Y"),
                        investment("c", "FIXED_INCOME", "300", null, null, "Banco Z")),
                "0");

        assertNull(service.overview(USER_ID, TODAY).concentration());
    }

    @Test
    void theEmergencyReferenceIsSixMonthsOfAverageExpensesAndCountsAccounts() {
        world(List.of(investment("a", "FIXED_INCOME", "5000.00", null, null, "X")), "1000.00",
                expenses("2000"), expenses("3000"), expenses("4000"));

        var fund = service.overview(USER_ID, TODAY).emergencyFund();

        assertEquals(new BigDecimal("3000.00"), fund.averageMonthlyExpenses());
        assertEquals(6, fund.referenceMonths());
        assertEquals(new BigDecimal("18000.00"), fund.referenceAmount());
        assertEquals(0, new BigDecimal("6000.00").compareTo(fund.availableAmount()));
        assertTrue(fund.includesAccounts());
        assertEquals(new BigDecimal("2.0"), fund.coveredMonths());
    }

    @Test
    void aNegativeBankBalanceDoesNotReduceWhatIsAvailable() {
        world(List.of(investment("a", "FIXED_INCOME", "5000.00", null, null, "X")), "-1500.00", expenses("2500"));

        var fund = service.overview(USER_ID, TODAY).emergencyFund();

        assertEquals(0, new BigDecimal("5000.00").compareTo(fund.availableAmount()));
        assertEquals(new BigDecimal("2.0"), fund.coveredMonths());
    }

    @Test
    void whenTheAccountsCannotBeReadOnlyTheInvestmentsCountAndItSaysSo() {
        world(List.of(investment("a", "FIXED_INCOME", "5000.00", null, null, "X")), null, expenses("2500"));

        var fund = service.overview(USER_ID, TODAY).emergencyFund();

        assertFalse(fund.includesAccounts());
        assertEquals(0, new BigDecimal("5000.00").compareTo(fund.availableAmount()));
    }

    @Test
    void withoutExpenseHistoryThereIsNoCoverage() {
        world(List.of(investment("a", "FIXED_INCOME", "5000.00", null, null, "X")), "0");

        var fund = service.overview(USER_ID, TODAY).emergencyFund();

        assertEquals(0, BigDecimal.ZERO.compareTo(fund.referenceAmount()));
        assertNull(fund.coveredMonths());
    }

    @Test
    void aConnectionThatFailsIsReportedAndTheOthersStillCount() {
        when(connectionRepository.findByUserIdOrderByCreatedAtDesc(USER_ID))
                .thenReturn(List.of(connection("item-1", "Nubank"), connection("item-2", "Inter")));
        when(provider.listInvestments("item-1")).thenThrow(new IllegalStateException("erro"));
        when(provider.listInvestments("item-2")).thenReturn(List.of(investment("a", "FIXED_INCOME", "100", null, null, "X")));
        when(transactionRepository.getMonthlyTotals(eq(USER_ID), any(), any())).thenReturn(List.of());
        when(accountsService.overview(USER_ID, TODAY)).thenReturn(
                new AccountsOverviewDTO(List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of()));

        InvestmentsOverviewDTO overview = service.overview(USER_ID, TODAY);

        assertEquals(List.of("Nubank"), overview.unavailableConnections());
        assertEquals(1, overview.investments().size());
    }

    @Test
    void noConnectionsMeansAnEmptyPortfolio() {
        when(connectionRepository.findByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of());
        when(transactionRepository.getMonthlyTotals(eq(USER_ID), any(), any())).thenReturn(List.of());
        when(accountsService.overview(USER_ID, TODAY)).thenReturn(
                new AccountsOverviewDTO(List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of()));

        InvestmentsOverviewDTO overview = service.overview(USER_ID, TODAY);

        assertTrue(overview.investments().isEmpty());
        assertTrue(overview.allocation().isEmpty());
        assertNull(overview.concentration());
        assertEquals(0, BigDecimal.ZERO.compareTo(overview.totalBalance()));
    }
}
