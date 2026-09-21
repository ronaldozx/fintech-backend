package com.globo.fintech_backend.Notifications.source;

import com.globo.fintech_backend.Goals.dto.GoalDTO;
import com.globo.fintech_backend.Goals.dto.GoalStatus;
import com.globo.fintech_backend.Goals.service.GoalService;
import com.globo.fintech_backend.Notifications.dto.NotificationDraft;
import com.globo.fintech_backend.Notifications.entity.NotificationSeverity;
import com.globo.fintech_backend.common.PtBr;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

@Component
public class GoalNotificationSource implements NotificationSource {

    private static final String TYPE = "GOAL";
    private static final String LINK = "/orcamentos";

    private final GoalService goalService;

    public GoalNotificationSource(GoalService goalService) {
        this.goalService = goalService;
    }

    @Override
    public List<NotificationDraft> drafts(Long userId, LocalDate today) {
        List<NotificationDraft> drafts = new ArrayList<>();
        String monthKey = YearMonth.from(today).toString();

        for (GoalDTO goal : goalService.overview(userId, today).goals()) {
            GoalStatus status = goal.status();

            if (status == GoalStatus.ACHIEVED) {
                drafts.add(new NotificationDraft(TYPE, NotificationSeverity.INFO,
                        "Meta atingida: " + goal.name(),
                        "Você juntou " + PtBr.money(goal.savedAmount()) + " e chegou ao objetivo de " + PtBr.money(goal.targetAmount()) + ".",
                        LINK, "goal-done:" + goal.id()));
            } else if (status == GoalStatus.OVERDUE) {
                drafts.add(new NotificationDraft(TYPE, NotificationSeverity.WARNING,
                        "Meta com prazo vencido: " + goal.name(),
                        "Faltam " + PtBr.money(goal.remaining()) + " e o prazo era " + PtBr.dayMonth(goal.targetDate()) + ". Você pode ajustar a data.",
                        LINK, "goal-overdue:" + goal.id()));
            } else if (status == GoalStatus.BEHIND) {
                drafts.add(new NotificationDraft(TYPE, NotificationSeverity.WARNING,
                        "Meta atrasada: " + goal.name(),
                        "Para chegar a " + PtBr.dayMonth(goal.targetDate()) + " seria preciso guardar cerca de "
                                + PtBr.money(goal.monthlyNeeded()) + " por mês.",
                        LINK, "goal-behind:" + goal.id() + ":" + monthKey));
            }
        }

        return drafts;
    }
}
