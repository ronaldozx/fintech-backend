package com.globo.fintech_backend.Advisor.service;

import com.globo.fintech_backend.Advisor.dto.AdviceItemDTO;
import com.globo.fintech_backend.Advisor.dto.AdviceSeverity;
import com.globo.fintech_backend.Advisor.dto.AdviceType;
import com.globo.fintech_backend.Budgets.dto.BudgetProgressDTO;
import com.globo.fintech_backend.Budgets.dto.BudgetStatus;
import com.globo.fintech_backend.Budgets.dto.BudgetsOverviewDTO;
import com.globo.fintech_backend.Budgets.service.BudgetService;
import com.globo.fintech_backend.Goals.dto.GoalDTO;
import com.globo.fintech_backend.Goals.dto.GoalStatus;
import com.globo.fintech_backend.Goals.dto.GoalsOverviewDTO;
import com.globo.fintech_backend.Goals.service.GoalService;
import com.globo.fintech_backend.Insights.dto.CashFlowInsight;
import com.globo.fintech_backend.Insights.dto.CategoryChange;
import com.globo.fintech_backend.Insights.dto.CategoryMovers;
import com.globo.fintech_backend.Insights.dto.InsightsDTO;
import com.globo.fintech_backend.Insights.dto.RecurringCharge;
import com.globo.fintech_backend.Insights.dto.RecurringInsight;
import com.globo.fintech_backend.Insights.dto.UnusualExpense;
import com.globo.fintech_backend.Insights.service.InsightsService;
import com.globo.fintech_backend.Investments.dto.ConcentrationDTO;
import com.globo.fintech_backend.Investments.dto.EmergencyFundDTO;
import com.globo.fintech_backend.Investments.dto.InvestmentsOverviewDTO;
import com.globo.fintech_backend.Investments.service.InvestmentsService;
import com.globo.fintech_backend.OpenFinance.accounts.AccountDTO;
import com.globo.fintech_backend.OpenFinance.accounts.AccountsOverviewDTO;
import com.globo.fintech_backend.OpenFinance.accounts.AccountsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdvisorServiceTest {

    private static final Long USER_ID = 7L;
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 21);

    @Mock
    private BudgetService budgetService;

    @Mock
    private InsightsService insightsService;

    @Mock
    private GoalService goalService;

    @Mock
    private InvestmentsService investmentsService;

    @Mock
    private AccountsService accountsService;

    private AdvisorService service;

    private static final InsightsDTO EMPTY_INSIGHTS = new InsightsDTO("2026-09",
            new CashFlowInsight(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, null),
            new CategoryMovers(List.of(), List.of()),
            new RecurringInsight(List.of(), BigDecimal.ZERO),
            List.of(), List.of(), null);

    private static final BudgetsOverviewDTO EMPTY_BUDGETS = new BudgetsOverviewDTO("2026-09", List.of(), List.of(), BigDecimal.ZERO);
    private static final GoalsOverviewDTO EMPTY_GOALS = new GoalsOverviewDTO(List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    private static final EmergencyFundDTO NO_FUND = new EmergencyFundDTO(BigDecimal.ZERO, 6, BigDecimal.ZERO, BigDecimal.ZERO, true, null);
    private static final InvestmentsOverviewDTO EMPTY_INVESTMENTS =
            new InvestmentsOverviewDTO(List.of(), BigDecimal.ZERO, null, null, List.of(), List.of(), null, NO_FUND, List.of());
    private static final AccountsOverviewDTO EMPTY_ACCOUNTS =
            new AccountsOverviewDTO(List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of());

    @BeforeEach
    void setUp() {
        service = new AdvisorService(budgetService, insightsService, goalService, investmentsService, accountsService);
        when(budgetService.overview(USER_ID, null, YearMonth.from(TODAY))).thenReturn(EMPTY_BUDGETS);
        when(insightsService.insights(USER_ID, null, TODAY)).thenReturn(EMPTY_INSIGHTS);
        when(goalService.overview(USER_ID, TODAY)).thenReturn(EMPTY_GOALS);
        when(investmentsService.overview(USER_ID, TODAY)).thenReturn(EMPTY_INVESTMENTS);
        when(accountsService.overview(USER_ID, TODAY)).thenReturn(EMPTY_ACCOUNTS);
    }

    private static BudgetProgressDTO budget(String category, String limit, String spent, int percent, BudgetStatus status) {
        BigDecimal limitValue = new BigDecimal(limit);
        BigDecimal spentValue = new BigDecimal(spent);
        return new BudgetProgressDTO(1L, category, limitValue, spentValue, limitValue.subtract(spentValue), percent, status);
    }

    @Test
    void withNoSignalsTheDisclaimerIsStillPresentAndTheListIsEmpty() {
        var advice = service.advise(USER_ID, TODAY);

        assertTrue(advice.items().isEmpty());
        assertTrue(advice.disclaimer().toLowerCase().contains("não é recomendação"));
    }

    @Test
    void anExceededBudgetIsCriticalAndAWarningOneIsNot() {
        when(budgetService.overview(USER_ID, null, YearMonth.from(TODAY))).thenReturn(new BudgetsOverviewDTO("2026-09",
                List.of(budget("Mercado", "500", "600", 120, BudgetStatus.EXCEEDED),
                        budget("Lazer", "200", "180", 90, BudgetStatus.WARNING),
                        budget("Transporte", "300", "100", 33, BudgetStatus.OK)),
                List.of(), BigDecimal.ZERO));

        var items = service.advise(USER_ID, TODAY).items();

        assertEquals(2, items.stream().filter(item -> item.type() == AdviceType.OVERSPENDING).count());
        AdviceItemDTO first = items.get(0);
        assertEquals(AdviceSeverity.CRITICAL, first.severity());
        assertTrue(first.message().contains("R$ 600,00"));
    }

    @Test
    void onlyTheTwoWorstBudgetsAreShown() {
        when(budgetService.overview(USER_ID, null, YearMonth.from(TODAY))).thenReturn(new BudgetsOverviewDTO("2026-09",
                List.of(budget("A", "100", "200", 200, BudgetStatus.EXCEEDED),
                        budget("B", "100", "150", 150, BudgetStatus.EXCEEDED),
                        budget("C", "100", "120", 120, BudgetStatus.EXCEEDED)),
                List.of(), BigDecimal.ZERO));

        var items = service.advise(USER_ID, TODAY).items();

        assertEquals(2, items.size());
        assertEquals("A", items.get(0).category());
        assertEquals("B", items.get(1).category());
    }

    @Test
    void aNegativeSavingsRateIsCriticalAndALowOneIsAWarning() {
        when(insightsService.insights(USER_ID, null, TODAY)).thenReturn(new InsightsDTO("2026-09",
                new CashFlowInsight(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, -20, null),
                new CategoryMovers(List.of(), List.of()), new RecurringInsight(List.of(), BigDecimal.ZERO), List.of(), List.of(), null));

        var negative = service.advise(USER_ID, TODAY).items();
        assertEquals(AdviceSeverity.CRITICAL, negative.get(0).severity());
        assertEquals(AdviceType.SAVINGS_RATE, negative.get(0).type());

        when(insightsService.insights(USER_ID, null, TODAY)).thenReturn(new InsightsDTO("2026-09",
                new CashFlowInsight(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 5, null),
                new CategoryMovers(List.of(), List.of()), new RecurringInsight(List.of(), BigDecimal.ZERO), List.of(), List.of(), null));
        var low = service.advise(USER_ID, TODAY).items();
        assertEquals(AdviceSeverity.WARNING, low.get(0).severity());
    }

    @Test
    void aHealthySavingsRateProducesNoAdvice() {
        when(insightsService.insights(USER_ID, null, TODAY)).thenReturn(new InsightsDTO("2026-09",
                new CashFlowInsight(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 30, null),
                new CategoryMovers(List.of(), List.of()), new RecurringInsight(List.of(), BigDecimal.ZERO), List.of(), List.of(), null));

        assertTrue(service.advise(USER_ID, TODAY).items().isEmpty());
    }

    @Test
    void aBigCategorySpikeIsFlaggedButATinyOneIsIgnored() {
        when(insightsService.insights(USER_ID, null, TODAY)).thenReturn(new InsightsDTO("2026-09",
                new CashFlowInsight(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, null),
                new CategoryMovers(List.of(
                        new CategoryChange("Lazer", new BigDecimal("300"), new BigDecimal("100"), new BigDecimal("200")),
                        new CategoryChange("Café", new BigDecimal("30"), new BigDecimal("20"), new BigDecimal("10"))),
                        List.of()),
                new RecurringInsight(List.of(), BigDecimal.ZERO), List.of(), List.of(), null));

        var items = service.advise(USER_ID, TODAY).items();

        assertEquals(1, items.size());
        assertEquals("Lazer", items.get(0).category());
    }

    @Test
    void unusualExpensesAndRecurringChargesBecomeAdvice() {
        when(insightsService.insights(USER_ID, null, TODAY)).thenReturn(new InsightsDTO("2026-09",
                new CashFlowInsight(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null, null),
                new CategoryMovers(List.of(), List.of()),
                new RecurringInsight(List.of(new RecurringCharge("Netflix", new BigDecimal("39.90"), 5, TODAY)), new BigDecimal("39.90")),
                List.of(new UnusualExpense("Atacadão", "Mercado", TODAY, new BigDecimal("500"), new BigDecimal("100"))),
                List.of(), null));

        var items = service.advise(USER_ID, TODAY).items();

        assertTrue(items.stream().anyMatch(item -> item.type() == AdviceType.UNUSUAL_EXPENSE));
        assertTrue(items.stream().anyMatch(item -> item.type() == AdviceType.RECURRING_REVIEW));
    }

    @Test
    void anOverdueGoalOutranksABehindOne() {
        GoalDTO overdue = new GoalDTO(1L, "Viagem", new BigDecimal("1000"), new BigDecimal("200"), new BigDecimal("800"), 20,
                TODAY.minusDays(1), GoalStatus.OVERDUE, null, null, null);
        GoalDTO behind = new GoalDTO(2L, "Carro", new BigDecimal("2000"), new BigDecimal("300"), new BigDecimal("1700"), 15,
                TODAY.plusMonths(3), GoalStatus.BEHIND, 3, new BigDecimal("566.67"), null);
        GoalDTO onTrack = new GoalDTO(3L, "Casa", new BigDecimal("5000"), new BigDecimal("2500"), new BigDecimal("2500"), 50,
                TODAY.plusMonths(6), GoalStatus.ON_TRACK, 6, new BigDecimal("416.67"), null);
        when(goalService.overview(USER_ID, TODAY)).thenReturn(new GoalsOverviewDTO(List.of(behind, overdue, onTrack), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));

        var items = service.advise(USER_ID, TODAY).items();

        assertEquals(2, items.size());
        assertTrue(items.get(0).title().contains("Viagem"));
    }

    @Test
    void aLowEmergencyFundAndAConcentratedPortfolioProduceAdvice() {
        EmergencyFundDTO fund = new EmergencyFundDTO(new BigDecimal("3000"), 6, new BigDecimal("18000"), new BigDecimal("6000"), true, new BigDecimal("2.0"));
        when(investmentsService.overview(USER_ID, TODAY)).thenReturn(new InvestmentsOverviewDTO(
                List.of(), BigDecimal.ZERO, null, null, List.of(), List.of(),
                new ConcentrationDTO("Banco X", 70), fund, List.of()));

        var items = service.advise(USER_ID, TODAY).items();

        assertTrue(items.stream().anyMatch(item -> item.type() == AdviceType.EMERGENCY_FUND));
        assertTrue(items.stream().anyMatch(item -> item.type() == AdviceType.CONCENTRATION && item.message().contains("70%")));
    }

    @Test
    void aFundThatAlreadyMeetsTheReferenceProducesNoAdvice() {
        EmergencyFundDTO fund = new EmergencyFundDTO(new BigDecimal("1000"), 6, new BigDecimal("6000"), new BigDecimal("9000"), true, new BigDecimal("9.0"));
        when(investmentsService.overview(USER_ID, TODAY)).thenReturn(new InvestmentsOverviewDTO(
                List.of(), BigDecimal.ZERO, null, null, List.of(), List.of(), null, fund, List.of()));

        assertTrue(service.advise(USER_ID, TODAY).items().isEmpty());
    }

    @Test
    void overdraftUseIsFlaggedAndOnlyTheBiggestOneIsShown() {
        AccountDTO small = new AccountDTO(1L, "Banco A", "Conta", "BANK", null, BigDecimal.ZERO, "BRL", null, null, null,
                new BigDecimal("500"), new BigDecimal("100"));
        AccountDTO big = new AccountDTO(2L, "Banco B", "Conta", "BANK", null, BigDecimal.ZERO, "BRL", null, null, null,
                new BigDecimal("1500"), new BigDecimal("1200"));
        when(accountsService.overview(USER_ID, TODAY)).thenReturn(new AccountsOverviewDTO(
                List.of(small, big), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of()));

        var items = service.advise(USER_ID, TODAY).items();

        assertEquals(1, items.size());
        assertTrue(items.get(0).title().contains("Banco B"));
    }

    @Test
    void oneFailingSourceDoesNotStopTheOthers() {
        when(budgetService.overview(USER_ID, null, YearMonth.from(TODAY))).thenThrow(new IllegalStateException("falhou"));
        when(insightsService.insights(USER_ID, null, TODAY)).thenReturn(new InsightsDTO("2026-09",
                new CashFlowInsight(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, -50, null),
                new CategoryMovers(List.of(), List.of()), new RecurringInsight(List.of(), BigDecimal.ZERO), List.of(), List.of(), null));

        var items = service.advise(USER_ID, TODAY).items();

        assertEquals(1, items.size());
        assertEquals(AdviceType.SAVINGS_RATE, items.get(0).type());
    }

    @Test
    void neverRecommendsASpecificInvestmentProduct() {
        when(budgetService.overview(USER_ID, null, YearMonth.from(TODAY))).thenReturn(new BudgetsOverviewDTO("2026-09",
                List.of(budget("Mercado", "500", "600", 120, BudgetStatus.EXCEEDED)), List.of(), BigDecimal.ZERO));
        EmergencyFundDTO fund = new EmergencyFundDTO(new BigDecimal("3000"), 6, new BigDecimal("18000"), new BigDecimal("6000"), true, new BigDecimal("2.0"));
        when(investmentsService.overview(USER_ID, TODAY)).thenReturn(new InvestmentsOverviewDTO(
                List.of(), BigDecimal.ZERO, null, null, List.of(), List.of(), new ConcentrationDTO("Banco X", 70), fund, List.of()));

        var items = service.advise(USER_ID, TODAY).items();

        for (AdviceItemDTO item : items) {
            for (String forbidden : List.of("compre", "invista em", "CDB do", "aplique em", "recomendo")) {
                assertTrue(!item.message().toLowerCase().contains(forbidden.toLowerCase())
                        && !item.title().toLowerCase().contains(forbidden.toLowerCase()));
            }
        }
    }
}
