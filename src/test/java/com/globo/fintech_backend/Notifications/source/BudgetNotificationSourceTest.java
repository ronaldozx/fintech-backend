package com.globo.fintech_backend.Notifications.source;

import com.globo.fintech_backend.Budgets.dto.BudgetProgressDTO;
import com.globo.fintech_backend.Budgets.dto.BudgetStatus;
import com.globo.fintech_backend.Budgets.dto.BudgetsOverviewDTO;
import com.globo.fintech_backend.Budgets.service.BudgetService;
import com.globo.fintech_backend.Notifications.dto.NotificationDraft;
import com.globo.fintech_backend.Notifications.entity.NotificationSeverity;
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
class BudgetNotificationSourceTest {

    private static final Long USER_ID = 7L;
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 21);

    @Mock
    private BudgetService budgetService;

    private static BudgetProgressDTO budget(long id, String category, String limit, String spent, int percent, BudgetStatus status) {
        BigDecimal limitValue = new BigDecimal(limit);
        BigDecimal spentValue = new BigDecimal(spent);
        return new BudgetProgressDTO(id, category, limitValue, spentValue, limitValue.subtract(spentValue), percent, status);
    }

    private List<NotificationDraft> drafts(BudgetProgressDTO... budgets) {
        when(budgetService.overview(USER_ID, null, YearMonth.of(2026, 9)))
                .thenReturn(new BudgetsOverviewDTO("2026-09", List.of(budgets), List.of(), BigDecimal.ZERO));
        return new BudgetNotificationSource(budgetService).drafts(USER_ID, TODAY);
    }

    @Test
    void healthyBudgetsProduceNothing() {
        assertTrue(drafts(budget(1, "Mercado", "500", "100", 20, BudgetStatus.OK)).isEmpty());
    }

    @Test
    void aBudgetNearItsLimitWarns() {
        NotificationDraft draft = drafts(budget(1, "Mercado", "500.00", "450.00", 90, BudgetStatus.WARNING)).get(0);

        assertEquals(NotificationSeverity.WARNING, draft.severity());
        assertEquals("Orçamento perto do limite: Mercado", draft.title());
        assertTrue(draft.message().contains("90%"));
        assertTrue(draft.message().contains("R$ 500,00"));
        assertTrue(draft.message().contains("R$ 50,00"));
        assertEquals("/orcamentos", draft.link());
        assertEquals("budget:1:2026-09:WARNING", draft.dedupeKey());
    }

    @Test
    void anExceededBudgetIsCriticalAndTheOverallOneIsNamedAsSuch() {
        NotificationDraft draft = drafts(budget(4, null, "2000.00", "2500.00", 125, BudgetStatus.EXCEEDED)).get(0);

        assertEquals(NotificationSeverity.CRITICAL, draft.severity());
        assertEquals("Orçamento estourado: Orçamento total", draft.title());
        assertEquals("budget:4:2026-09:EXCEEDED", draft.dedupeKey());
    }

    @Test
    void movingFromWarningToExceededProducesADifferentKeySoItNotifiesAgain() {
        String warning = drafts(budget(1, "Mercado", "500", "450", 90, BudgetStatus.WARNING)).get(0).dedupeKey();
        String exceeded = drafts(budget(1, "Mercado", "500", "550", 110, BudgetStatus.EXCEEDED)).get(0).dedupeKey();

        assertTrue(!warning.equals(exceeded));
    }
}
