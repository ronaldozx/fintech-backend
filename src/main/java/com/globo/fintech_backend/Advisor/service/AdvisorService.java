package com.globo.fintech_backend.Advisor.service;

import com.globo.fintech_backend.Advisor.dto.AdviceItemDTO;
import com.globo.fintech_backend.Advisor.dto.AdviceOverviewDTO;
import com.globo.fintech_backend.Advisor.dto.AdviceSeverity;
import com.globo.fintech_backend.Advisor.dto.AdviceType;
import com.globo.fintech_backend.Budgets.dto.BudgetProgressDTO;
import com.globo.fintech_backend.Budgets.dto.BudgetStatus;
import com.globo.fintech_backend.Budgets.dto.BudgetsOverviewDTO;
import com.globo.fintech_backend.Budgets.service.BudgetService;
import com.globo.fintech_backend.Goals.dto.GoalDTO;
import com.globo.fintech_backend.Goals.dto.GoalStatus;
import com.globo.fintech_backend.Goals.service.GoalService;
import com.globo.fintech_backend.Insights.dto.CategoryChange;
import com.globo.fintech_backend.Insights.dto.InsightsDTO;
import com.globo.fintech_backend.Insights.dto.RecurringCharge;
import com.globo.fintech_backend.Insights.dto.UnusualExpense;
import com.globo.fintech_backend.Insights.service.InsightsService;
import com.globo.fintech_backend.Investments.dto.InvestmentsOverviewDTO;
import com.globo.fintech_backend.Investments.service.InvestmentsService;
import com.globo.fintech_backend.OpenFinance.accounts.AccountDTO;
import com.globo.fintech_backend.OpenFinance.accounts.AccountsOverviewDTO;
import com.globo.fintech_backend.OpenFinance.accounts.AccountsService;
import com.globo.fintech_backend.common.PtBr;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class AdvisorService {

    public static final String DISCLAIMER =
            "Geradas por regras a partir dos seus próprios dados, sem inteligência artificial externa. "
                    + "Não é recomendação de investimento nem consultoria financeira; para decidir onde investir, converse com um profissional habilitado.";

    static final int MAX_ITEMS = 12;
    static final int MAX_PER_TYPE = 2;
    static final int LOW_SAVINGS_RATE = 10;
    static final BigDecimal MIN_SPIKE_AMOUNT = new BigDecimal("50");

    private static final Logger log = LoggerFactory.getLogger(AdvisorService.class);
    private static final String BUDGETS_LINK = "/orcamentos";
    private static final String INSIGHTS_LINK = "/insights";
    private static final String INVESTMENTS_LINK = "/investimentos";
    private static final String ACCOUNTS_LINK = "/contas";

    private final BudgetService budgetService;
    private final InsightsService insightsService;
    private final GoalService goalService;
    private final InvestmentsService investmentsService;
    private final AccountsService accountsService;

    public AdvisorService(BudgetService budgetService,
                          InsightsService insightsService,
                          GoalService goalService,
                          InvestmentsService investmentsService,
                          AccountsService accountsService) {
        this.budgetService = budgetService;
        this.insightsService = insightsService;
        this.goalService = goalService;
        this.investmentsService = investmentsService;
        this.accountsService = accountsService;
    }

    public AdviceOverviewDTO advise(Long userId, LocalDate today) {
        List<AdviceItemDTO> items = new ArrayList<>();

        collect(items, () -> overspending(userId, today), "budgets");
        collect(items, () -> insightsAdvice(userId, today), "insights");
        collect(items, () -> goalPace(userId, today), "goals");
        collect(items, () -> investmentsAdvice(userId, today), "investments");
        collect(items, () -> overdraftCost(userId, today), "accounts");

        List<AdviceItemDTO> ranked = items.stream()
                .sorted(Comparator.comparing((AdviceItemDTO item) -> item.severity().ordinal())
                        .thenComparing(item -> item.type().ordinal()))
                .limit(MAX_ITEMS)
                .toList();

        return new AdviceOverviewDTO(ranked, DISCLAIMER);
    }

    private interface Rule {
        List<AdviceItemDTO> run();
    }

    private void collect(List<AdviceItemDTO> items, Rule rule, String source) {
        try {
            items.addAll(rule.run());
        } catch (RuntimeException e) {
            log.warn("Advisor rule '{}' failed: {}", source, e.getMessage());
        }
    }

    private List<AdviceItemDTO> overspending(Long userId, LocalDate today) {
        BudgetsOverviewDTO overview = budgetService.overview(userId, null, YearMonth.from(today));

        return overview.budgets().stream()
                .filter(budget -> budget.status() != BudgetStatus.OK)
                .sorted(Comparator.comparingInt(BudgetProgressDTO::percent).reversed())
                .limit(MAX_PER_TYPE)
                .map(AdvisorService::toOverspendingAdvice)
                .toList();
    }

    private static AdviceItemDTO toOverspendingAdvice(BudgetProgressDTO budget) {
        String name = budget.category() == null ? "seu orçamento total" : budget.category();
        boolean exceeded = budget.status() == BudgetStatus.EXCEEDED;

        String message = exceeded
                ? "Você já gastou " + PtBr.money(budget.spent()) + " em " + name + ", " + budget.percent()
                        + "% do limite de " + PtBr.money(budget.monthlyLimit()) + ". Considere segurar esse gasto no resto do mês."
                : "Você usou " + budget.percent() + "% do limite de " + name + " (" + PtBr.money(budget.monthlyLimit())
                        + "). Restam " + PtBr.money(budget.remaining()) + " para não estourar.";

        return new AdviceItemDTO(
                AdviceType.OVERSPENDING,
                exceeded ? AdviceSeverity.CRITICAL : AdviceSeverity.WARNING,
                budget.category(),
                exceeded ? "Orçamento estourado: " + name : "Perto de estourar: " + name,
                message,
                BUDGETS_LINK);
    }

    private List<AdviceItemDTO> insightsAdvice(Long userId, LocalDate today) {
        InsightsDTO insights = insightsService.insights(userId, null, today);
        List<AdviceItemDTO> advice = new ArrayList<>();

        advice.addAll(savingsRateAdvice(insights));
        advice.addAll(categorySpikeAdvice(insights.movers().increases()));
        advice.addAll(unusualAdvice(insights.unusual()));
        recurringAdvice(insights.recurring().charges(), insights.recurring().monthlyTotal()).ifPresent(advice::add);

        return advice;
    }

    private static List<AdviceItemDTO> savingsRateAdvice(InsightsDTO insights) {
        Integer rate = insights.cashFlow().savingsRate();
        if (rate == null) {
            return List.of();
        }

        if (rate < 0) {
            return List.of(new AdviceItemDTO(
                    AdviceType.SAVINGS_RATE, AdviceSeverity.CRITICAL, null,
                    "Você gastou mais do que ganhou este mês",
                    "Suas despesas ficaram " + Math.abs(rate) + "% acima das receitas do mês. Veja em Insights quais categorias mais pesaram.",
                    INSIGHTS_LINK));
        }
        if (rate < LOW_SAVINGS_RATE) {
            return List.of(new AdviceItemDTO(
                    AdviceType.SAVINGS_RATE, AdviceSeverity.WARNING, null,
                    "Taxa de poupança baixa",
                    "Você guardou " + rate + "% da sua receita este mês. Cortar um pouco nas categorias que mais cresceram pode ajudar.",
                    INSIGHTS_LINK));
        }
        return List.of();
    }

    private static List<AdviceItemDTO> categorySpikeAdvice(List<CategoryChange> increases) {
        return increases.stream()
                .filter(change -> change.change().compareTo(MIN_SPIKE_AMOUNT) >= 0)
                .limit(MAX_PER_TYPE)
                .map(change -> new AdviceItemDTO(
                        AdviceType.CATEGORY_SPIKE, AdviceSeverity.WARNING, change.category(),
                        "Gasto crescente: " + change.category(),
                        "Você gastou " + PtBr.money(change.change()) + " a mais em " + change.category()
                                + " do que no mês passado (de " + PtBr.money(change.previous()) + " para " + PtBr.money(change.current()) + ").",
                        INSIGHTS_LINK))
                .toList();
    }

    private static List<AdviceItemDTO> unusualAdvice(List<UnusualExpense> unusual) {
        return unusual.stream()
                .limit(MAX_PER_TYPE)
                .map(expense -> new AdviceItemDTO(
                        AdviceType.UNUSUAL_EXPENSE, AdviceSeverity.WARNING, expense.category(),
                        "Gasto fora do padrão: " + expense.description(),
                        PtBr.money(expense.amount()) + " em " + expense.category() + " em " + PtBr.dayMonth(expense.date())
                                + ", bem acima do habitual de " + PtBr.money(expense.typicalAmount()) + " nessa categoria.",
                        INSIGHTS_LINK))
                .toList();
    }

    private static java.util.Optional<AdviceItemDTO> recurringAdvice(List<RecurringCharge> charges, BigDecimal monthlyTotal) {
        if (charges.isEmpty()) {
            return java.util.Optional.empty();
        }

        return java.util.Optional.of(new AdviceItemDTO(
                AdviceType.RECURRING_REVIEW, AdviceSeverity.INFO, null,
                "Revise suas cobranças fixas",
                "Você tem " + charges.size() + " cobrança(s) mensal(is) somando " + PtBr.money(monthlyTotal)
                        + " por mês, como " + charges.get(0).description()
                        + ". Vale conferir se ainda usa todas.",
                INSIGHTS_LINK));
    }

    private List<AdviceItemDTO> goalPace(Long userId, LocalDate today) {
        return goalService.overview(userId, today).goals().stream()
                .filter(goal -> goal.status() == GoalStatus.BEHIND || goal.status() == GoalStatus.OVERDUE)
                .sorted(Comparator.comparing((GoalDTO goal) -> goal.status() == GoalStatus.OVERDUE ? 0 : 1))
                .limit(MAX_PER_TYPE)
                .map(AdvisorService::toGoalAdvice)
                .toList();
    }

    private static AdviceItemDTO toGoalAdvice(GoalDTO goal) {
        boolean overdue = goal.status() == GoalStatus.OVERDUE;
        String message = overdue
                ? "O prazo de " + PtBr.dayMonth(goal.targetDate()) + " passou e faltam " + PtBr.money(goal.remaining())
                        + ". Considere ajustar a data ou aumentar os aportes."
                : "No ritmo atual, você não deve chegar a tempo. Para cumprir o prazo de " + PtBr.dayMonth(goal.targetDate())
                        + ", seria preciso guardar cerca de " + PtBr.money(goal.monthlyNeeded()) + " por mês.";

        return new AdviceItemDTO(
                AdviceType.GOAL_PACE,
                overdue ? AdviceSeverity.WARNING : AdviceSeverity.INFO,
                null,
                (overdue ? "Meta com prazo vencido: " : "Meta atrasada: ") + goal.name(),
                message,
                BUDGETS_LINK);
    }

    private List<AdviceItemDTO> investmentsAdvice(Long userId, LocalDate today) {
        InvestmentsOverviewDTO overview = investmentsService.overview(userId, today);
        List<AdviceItemDTO> advice = new ArrayList<>();

        var fund = overview.emergencyFund();
        if (fund.coveredMonths() != null && fund.coveredMonths().compareTo(BigDecimal.valueOf(fund.referenceMonths())) < 0) {
            advice.add(new AdviceItemDTO(
                    AdviceType.EMERGENCY_FUND, AdviceSeverity.INFO, null,
                    "Reserva de emergência abaixo da referência",
                    "Sua reserva cobre cerca de " + fund.coveredMonths().toPlainString() + " meses de despesas; a referência comum é "
                            + fund.referenceMonths() + " meses. Muita gente prioriza completar a reserva antes de outros investimentos.",
                    INVESTMENTS_LINK));
        }

        if (overview.concentration() != null) {
            advice.add(new AdviceItemDTO(
                    AdviceType.CONCENTRATION, AdviceSeverity.WARNING, null,
                    "Carteira concentrada em " + overview.concentration().name(),
                    overview.concentration().percent() + "% dos seus investimentos estão em " + overview.concentration().name()
                            + ". Diversificar entre emissores reduz o impacto de um problema isolado.",
                    INVESTMENTS_LINK));
        }

        return advice;
    }

    private List<AdviceItemDTO> overdraftCost(Long userId, LocalDate today) {
        AccountsOverviewDTO overview = accountsService.overview(userId, today);

        return overview.accounts().stream()
                .filter(account -> account.overdraftUsed() != null && account.overdraftUsed().signum() > 0)
                .sorted(Comparator.comparing(AccountDTO::overdraftUsed).reversed())
                .limit(1)
                .map(account -> new AdviceItemDTO(
                        AdviceType.OVERDRAFT_COST, AdviceSeverity.WARNING, null,
                        "Cheque especial em uso: " + account.institution(),
                        "Você está usando " + PtBr.money(account.overdraftUsed()) + " do cheque especial no " + account.institution()
                                + ". É uma das formas de crédito mais caras; quitar essa dívida costuma valer mais a pena do que investir enquanto ela existir.",
                        ACCOUNTS_LINK))
                .toList();
    }
}
