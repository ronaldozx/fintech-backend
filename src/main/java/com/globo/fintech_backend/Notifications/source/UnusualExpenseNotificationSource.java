package com.globo.fintech_backend.Notifications.source;

import com.globo.fintech_backend.Insights.dto.UnusualExpense;
import com.globo.fintech_backend.Insights.service.InsightsService;
import com.globo.fintech_backend.Notifications.dto.NotificationDraft;
import com.globo.fintech_backend.Notifications.entity.NotificationSeverity;
import com.globo.fintech_backend.common.PtBr;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class UnusualExpenseNotificationSource implements NotificationSource {

    static final int RECENT_DAYS = 7;
    private static final String TYPE = "UNUSUAL";
    private static final String LINK = "/insights";

    private final InsightsService insightsService;

    public UnusualExpenseNotificationSource(InsightsService insightsService) {
        this.insightsService = insightsService;
    }

    @Override
    public List<NotificationDraft> drafts(Long userId, LocalDate today) {
        LocalDate since = today.minusDays(RECENT_DAYS);

        return insightsService.insights(userId, null, today).unusual().stream()
                .filter(expense -> !expense.date().isBefore(since))
                .map(UnusualExpenseNotificationSource::toDraft)
                .toList();
    }

    private static NotificationDraft toDraft(UnusualExpense expense) {
        return new NotificationDraft(
                TYPE,
                NotificationSeverity.WARNING,
                "Gasto fora do padrão: " + expense.description(),
                PtBr.money(expense.amount()) + " em " + expense.category() + " em " + PtBr.dayMonth(expense.date())
                        + ". Costuma ser " + PtBr.money(expense.typicalAmount()) + " nessa categoria.",
                LINK,
                "unusual:" + expense.date() + ":" + expense.description() + ":" + expense.amount());
    }
}
