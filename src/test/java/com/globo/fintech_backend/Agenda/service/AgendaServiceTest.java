package com.globo.fintech_backend.Agenda.service;

import com.globo.fintech_backend.Agenda.dto.AgendaDTO;
import com.globo.fintech_backend.Agenda.dto.AgendaItemDTO;
import com.globo.fintech_backend.Agenda.dto.AgendaItemType;
import com.globo.fintech_backend.Goals.dto.GoalDTO;
import com.globo.fintech_backend.Goals.dto.GoalStatus;
import com.globo.fintech_backend.Goals.dto.GoalsOverviewDTO;
import com.globo.fintech_backend.Goals.service.GoalService;
import com.globo.fintech_backend.Insights.dto.InsightsDTO;
import com.globo.fintech_backend.Insights.dto.RecurringCharge;
import com.globo.fintech_backend.Insights.dto.RecurringInsight;
import com.globo.fintech_backend.Insights.service.InsightsService;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgendaServiceTest {

    private static final Long USER_ID = 7L;
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 21);

    @Mock
    private AccountsService accountsService;

    @Mock
    private InsightsService insightsService;

    @Mock
    private GoalService goalService;

    private AgendaService service;

    @BeforeEach
    void setUp() {
        service = new AgendaService(accountsService, insightsService, goalService);
    }

    private static AccountDTO card(String name, String balance, LocalDate due) {
        return new AccountDTO(1L, "Nubank", name, "CREDIT", "•••• 1", new BigDecimal(balance), "BRL", null, null, due, null, null);
    }

    private static AccountDTO bank() {
        return new AccountDTO(1L, "Nubank", "Conta", "BANK", "•••• 2", new BigDecimal("100"), "BRL", null, null, null, null, null);
    }

    private static GoalDTO goal(String name, LocalDate date, GoalStatus status, String remaining) {
        return new GoalDTO(1L, name, new BigDecimal("1000"), new BigDecimal("500"), new BigDecimal(remaining), 50, date, status, 2, null, null);
    }

    private void data(List<AccountDTO> accounts, List<RecurringCharge> charges, List<GoalDTO> goals) {
        when(accountsService.overview(USER_ID, TODAY))
                .thenReturn(new AccountsOverviewDTO(accounts, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of()));
        when(insightsService.insights(USER_ID, null, TODAY))
                .thenReturn(new InsightsDTO("2026-09", null, null, new RecurringInsight(charges, BigDecimal.ZERO), List.of(), List.of(), null));
        when(goalService.overview(USER_ID, TODAY))
                .thenReturn(new GoalsOverviewDTO(goals, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    @Test
    void nextOccurrenceFollowsTheMonthlyRhythm() {
        assertEquals(LocalDate.of(2026, 10, 8), AgendaService.nextOccurrence(LocalDate.of(2026, 9, 8), TODAY));
        assertEquals(LocalDate.of(2026, 9, 25), AgendaService.nextOccurrence(LocalDate.of(2026, 8, 25), TODAY));
        assertEquals(LocalDate.of(2026, 10, 21), AgendaService.nextOccurrence(LocalDate.of(2026, 9, 21), TODAY));
        assertEquals(LocalDate.of(2026, 9, 30), AgendaService.nextOccurrence(LocalDate.of(2026, 7, 31), TODAY));
    }

    @Test
    void listsUpcomingBillsRecurringChargesAndGoalDeadlinesInDateOrder() {
        data(List.of(card("Cartão Gold", "1200.00", LocalDate.of(2026, 10, 5)), bank()),
                List.of(new RecurringCharge("Netflix", new BigDecimal("39.90"), 7, LocalDate.of(2026, 9, 8))),
                List.of(goal("Viagem", LocalDate.of(2026, 10, 1), GoalStatus.ON_TRACK, "500.00")));

        AgendaDTO agenda = service.agenda(USER_ID, null, TODAY);

        assertEquals(30, agenda.days());
        assertEquals(List.of(AgendaItemType.GOAL_DEADLINE, AgendaItemType.CARD_BILL, AgendaItemType.RECURRING),
                agenda.items().stream().map(AgendaItemDTO::type).toList());
        assertEquals(LocalDate.of(2026, 10, 1), agenda.items().get(0).date());
        assertEquals(10, agenda.items().get(0).daysUntil());
        assertEquals(new BigDecimal("1200.00"), agenda.billsTotal());
        assertEquals(new BigDecimal("39.90"), agenda.recurringTotal());
        assertFalse(agenda.accountsUnavailable());
    }

    @Test
    void ignoresBillsThatArePaidPastOrBeyondTheHorizon() {
        data(List.of(
                        card("Paga", "0", LocalDate.of(2026, 9, 25)),
                        card("Vencida", "300", LocalDate.of(2026, 9, 20)),
                        card("Longe", "300", LocalDate.of(2026, 12, 25)),
                        card("Sem data", "300", null)),
                List.of(), List.of());

        assertTrue(service.agenda(USER_ID, null, TODAY).items().isEmpty());
    }

    @Test
    void aBillDueTodayIsIncluded() {
        data(List.of(card("Hoje", "300", TODAY)), List.of(), List.of());

        AgendaItemDTO item = service.agenda(USER_ID, null, TODAY).items().get(0);

        assertEquals(0, item.daysUntil());
    }

    @Test
    void achievedAndFarGoalsAreLeftOut() {
        data(List.of(), List.of(), List.of(
                goal("Feita", LocalDate.of(2026, 10, 1), GoalStatus.ACHIEVED, "0.00"),
                goal("Longe", LocalDate.of(2027, 6, 1), GoalStatus.ON_TRACK, "500.00"),
                goal("Sem prazo", null, GoalStatus.OPEN, "500.00")));

        assertTrue(service.agenda(USER_ID, null, TODAY).items().isEmpty());
    }

    @Test
    void theHorizonIsClampedAndFiltersRecurringCharges() {
        data(List.of(), List.of(new RecurringCharge("Aluguel", new BigDecimal("1800.00"), 6, LocalDate.of(2026, 9, 10))), List.of());

        assertEquals(7, service.agenda(USER_ID, 1, TODAY).days());
        assertEquals(90, service.agenda(USER_ID, 500, TODAY).days());
        assertTrue(service.agenda(USER_ID, 7, TODAY).items().isEmpty());
        assertEquals(1, service.agenda(USER_ID, 30, TODAY).items().size());
    }

    @Test
    void unavailableAccountsStillReturnTheRestAndSayThatBillsAreMissing() {
        when(accountsService.overview(USER_ID, TODAY)).thenThrow(new IllegalStateException("Pluggy fora do ar"));
        when(insightsService.insights(USER_ID, null, TODAY))
                .thenReturn(new InsightsDTO("2026-09", null, null,
                        new RecurringInsight(List.of(new RecurringCharge("Netflix", new BigDecimal("39.90"), 7, LocalDate.of(2026, 9, 8))), BigDecimal.ZERO),
                        List.of(), List.of(), null));
        when(goalService.overview(USER_ID, TODAY)).thenReturn(new GoalsOverviewDTO(List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));

        AgendaDTO agenda = service.agenda(USER_ID, null, TODAY);

        assertTrue(agenda.accountsUnavailable());
        assertEquals(1, agenda.items().size());
    }

    @Test
    void aBankThatCouldNotBeReadAlsoFlagsThatBillsMayBeMissing() {
        when(accountsService.overview(USER_ID, TODAY)).thenReturn(new AccountsOverviewDTO(
                List.of(card("Cartão Gold", "1200.00", LocalDate.of(2026, 10, 5))),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of("Santander")));
        when(insightsService.insights(USER_ID, null, TODAY))
                .thenReturn(new InsightsDTO("2026-09", null, null, new RecurringInsight(List.of(), BigDecimal.ZERO), List.of(), List.of(), null));
        when(goalService.overview(USER_ID, TODAY)).thenReturn(new GoalsOverviewDTO(List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));

        AgendaDTO agenda = service.agenda(USER_ID, null, TODAY);

        assertTrue(agenda.accountsUnavailable());
        assertEquals(1, agenda.items().size());
    }
}
