package com.globo.fintech_backend.Agenda.service;

import com.globo.fintech_backend.Agenda.dto.AgendaDTO;
import com.globo.fintech_backend.Agenda.dto.AgendaItemDTO;
import com.globo.fintech_backend.Agenda.dto.AgendaItemType;
import com.globo.fintech_backend.Goals.dto.GoalDTO;
import com.globo.fintech_backend.Goals.dto.GoalStatus;
import com.globo.fintech_backend.Goals.service.GoalService;
import com.globo.fintech_backend.Insights.dto.RecurringCharge;
import com.globo.fintech_backend.Insights.service.InsightsService;
import com.globo.fintech_backend.OpenFinance.accounts.AccountsOverviewDTO;
import com.globo.fintech_backend.OpenFinance.accounts.AccountsService;
import com.globo.fintech_backend.common.PtBr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

@Service
public class AgendaService {

    public static final int DEFAULT_DAYS = 30;
    static final int MIN_DAYS = 7;
    static final int MAX_DAYS = 90;

    private static final Logger log = LoggerFactory.getLogger(AgendaService.class);
    private static final String CREDIT = "CREDIT";

    private final AccountsService accountsService;
    private final InsightsService insightsService;
    private final GoalService goalService;

    public AgendaService(AccountsService accountsService, InsightsService insightsService, GoalService goalService) {
        this.accountsService = accountsService;
        this.insightsService = insightsService;
        this.goalService = goalService;
    }

    public AgendaDTO agenda(Long userId, Integer requestedDays, LocalDate today) {
        int days = Math.min(MAX_DAYS, Math.max(MIN_DAYS, requestedDays == null ? DEFAULT_DAYS : requestedDays));
        LocalDate end = today.plusDays(days);

        List<AgendaItemDTO> items = new ArrayList<>();
        boolean accountsUnavailable = false;

        try {
            AccountsOverviewDTO overview = accountsService.overview(userId, today);
            items.addAll(bills(overview, today, end));
            accountsUnavailable = !overview.unavailableConnections().isEmpty();
        } catch (RuntimeException e) {
            log.warn("Could not read the card bills for user {}: {}", userId, e.getMessage());
            accountsUnavailable = true;
        }

        items.addAll(recurring(userId, today, end));
        items.addAll(goalDeadlines(userId, today, end));

        items.sort(Comparator.comparing(AgendaItemDTO::date).thenComparing(AgendaItemDTO::type).thenComparing(AgendaItemDTO::title));

        return new AgendaDTO(
                days,
                items,
                total(items, AgendaItemType.CARD_BILL),
                total(items, AgendaItemType.RECURRING),
                accountsUnavailable);
    }

    static LocalDate nextOccurrence(LocalDate lastSeen, LocalDate today) {
        int months = 1;
        LocalDate next = lastSeen.plusMonths(months);
        while (next.isBefore(today)) {
            next = lastSeen.plusMonths(++months);
        }
        return next;
    }

    private List<AgendaItemDTO> bills(AccountsOverviewDTO overview, LocalDate today, LocalDate end) {
        return overview.accounts().stream()
                .filter(account -> CREDIT.equals(account.type()))
                .filter(account -> account.dueDate() != null && account.balance() != null && account.balance().signum() > 0)
                .filter(account -> !account.dueDate().isBefore(today) && !account.dueDate().isAfter(end))
                .map(account -> item(AgendaItemType.CARD_BILL, "Fatura " + account.name(), account.institution(),
                        account.dueDate(), today, account.balance()))
                .toList();
    }

    private List<AgendaItemDTO> recurring(Long userId, LocalDate today, LocalDate end) {
        return insightsService.insights(userId, null, today).recurring().charges().stream()
                .map(charge -> item(AgendaItemType.RECURRING, charge.description(), subtitle(charge),
                        nextOccurrence(charge.lastDate(), today), today, charge.averageAmount()))
                .filter(within(end))
                .toList();
    }

    private List<AgendaItemDTO> goalDeadlines(Long userId, LocalDate today, LocalDate end) {
        return goalService.overview(userId, today).goals().stream()
                .filter(goal -> goal.targetDate() != null && goal.status() != GoalStatus.ACHIEVED)
                .filter(goal -> !goal.targetDate().isBefore(today) && !goal.targetDate().isAfter(end))
                .map(goal -> item(AgendaItemType.GOAL_DEADLINE, "Prazo da meta " + goal.name(), goalSubtitle(goal),
                        goal.targetDate(), today, goal.remaining()))
                .toList();
    }

    private static Predicate<AgendaItemDTO> within(LocalDate end) {
        return item -> !item.date().isAfter(end);
    }

    private static AgendaItemDTO item(AgendaItemType type, String title, String subtitle, LocalDate date, LocalDate today, BigDecimal amount) {
        return new AgendaItemDTO(type, title, subtitle, date, ChronoUnit.DAYS.between(today, date), amount);
    }

    private static String subtitle(RecurringCharge charge) {
        return "Cobrança mensal prevista, vista em " + charge.months() + " meses";
    }

    private static String goalSubtitle(GoalDTO goal) {
        return "Faltam " + PtBr.money(goal.remaining()) + " para chegar ao valor da meta";
    }

    private static BigDecimal total(List<AgendaItemDTO> items, AgendaItemType type) {
        return items.stream()
                .filter(item -> item.type() == type)
                .map(AgendaItemDTO::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
