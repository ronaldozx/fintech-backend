package com.globo.fintech_backend.Notifications.source;

import com.globo.fintech_backend.Goals.dto.GoalDTO;
import com.globo.fintech_backend.Goals.dto.GoalStatus;
import com.globo.fintech_backend.Goals.dto.GoalsOverviewDTO;
import com.globo.fintech_backend.Goals.service.GoalService;
import com.globo.fintech_backend.Notifications.dto.NotificationDraft;
import com.globo.fintech_backend.Notifications.entity.NotificationSeverity;
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
class GoalNotificationSourceTest {

    private static final Long USER_ID = 7L;
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 21);

    @Mock
    private GoalService goalService;

    private List<NotificationDraft> drafts(GoalStatus status) {
        GoalDTO goal = new GoalDTO(4L, "Viagem", new BigDecimal("5000"), new BigDecimal("1200"), new BigDecimal("3800"), 24,
                TODAY.plusMonths(2), status, 2, new BigDecimal("1900.00"), null);
        when(goalService.overview(USER_ID, TODAY))
                .thenReturn(new GoalsOverviewDTO(List.of(goal), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        return new GoalNotificationSource(goalService).drafts(USER_ID, TODAY);
    }

    @Test
    void anAchievedGoalIsCelebratedOnce() {
        NotificationDraft draft = drafts(GoalStatus.ACHIEVED).get(0);

        assertEquals(NotificationSeverity.INFO, draft.severity());
        assertEquals("Meta atingida: Viagem", draft.title());
        assertEquals("goal-done:4", draft.dedupeKey());
    }

    @Test
    void aBehindGoalWarnsOncePerMonthWithTheMonthlyNeed() {
        NotificationDraft draft = drafts(GoalStatus.BEHIND).get(0);

        assertEquals(NotificationSeverity.WARNING, draft.severity());
        assertTrue(draft.message().contains("R$ 1.900,00"));
        assertEquals("goal-behind:4:2026-09", draft.dedupeKey());
    }

    @Test
    void anOverdueGoalWarnsOnce() {
        NotificationDraft draft = drafts(GoalStatus.OVERDUE).get(0);

        assertEquals("Meta com prazo vencido: Viagem", draft.title());
        assertEquals("goal-overdue:4", draft.dedupeKey());
    }

    @Test
    void goalsOnTrackOrWithoutDeadlineAreQuiet() {
        assertTrue(drafts(GoalStatus.ON_TRACK).isEmpty());
        assertTrue(drafts(GoalStatus.OPEN).isEmpty());
    }
}
