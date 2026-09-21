package com.globo.fintech_backend.Notifications.source;

import com.globo.fintech_backend.Budgets.dto.BudgetProgressDTO;
import com.globo.fintech_backend.Budgets.dto.BudgetStatus;
import com.globo.fintech_backend.Budgets.dto.BudgetsOverviewDTO;
import com.globo.fintech_backend.Budgets.service.BudgetService;
import com.globo.fintech_backend.Notifications.dto.NotificationDraft;
import com.globo.fintech_backend.Notifications.entity.NotificationSeverity;
import com.globo.fintech_backend.common.PtBr;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Component
public class BudgetNotificationSource implements NotificationSource {

    private static final String TYPE = "BUDGET";
    private static final String LINK = "/orcamentos";

    private final BudgetService budgetService;

    public BudgetNotificationSource(BudgetService budgetService) {
        this.budgetService = budgetService;
    }

    @Override
    public List<NotificationDraft> drafts(Long userId, LocalDate today) {
        YearMonth month = YearMonth.from(today);
        BudgetsOverviewDTO overview = budgetService.overview(userId, null, month);

        return overview.budgets().stream()
                .filter(budget -> budget.status() != BudgetStatus.OK)
                .map(budget -> toDraft(budget, overview.month(), month))
                .toList();
    }

    private static NotificationDraft toDraft(BudgetProgressDTO budget, String monthKey, YearMonth month) {
        String name = budget.category() == null ? "Orçamento total" : budget.category();
        boolean exceeded = budget.status() == BudgetStatus.EXCEEDED;

        String title = exceeded ? "Orçamento estourado: " + name : "Orçamento perto do limite: " + name;
        String message = exceeded
                ? "Você gastou " + PtBr.money(budget.spent()) + " de um limite de " + PtBr.money(budget.monthlyLimit())
                        + " (" + budget.percent() + "%) em " + PtBr.month(month) + "."
                : "Você já usou " + budget.percent() + "% do limite de " + PtBr.money(budget.monthlyLimit())
                        + " em " + PtBr.month(month) + ". Restam " + PtBr.money(budget.remaining()) + ".";

        return new NotificationDraft(
                TYPE,
                exceeded ? NotificationSeverity.CRITICAL : NotificationSeverity.WARNING,
                title,
                message,
                LINK,
                "budget:" + budget.id() + ":" + monthKey + ":" + budget.status());
    }
}
