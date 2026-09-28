package com.globo.fintech_backend.Notifications.source;

import com.globo.fintech_backend.Insights.dto.InsightsDTO;
import com.globo.fintech_backend.Insights.dto.UnusualExpense;
import com.globo.fintech_backend.Insights.service.InsightsService;
import com.globo.fintech_backend.Notifications.dto.NotificationDraft;
import com.globo.fintech_backend.Notifications.entity.NotificationSeverity;
import com.globo.fintech_backend.OpenFinance.connection.BankConnection;
import com.globo.fintech_backend.OpenFinance.connection.BankConnectionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SyncAndUnusualNotificationSourceTest {

    private static final Long USER_ID = 7L;
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 21);

    @Mock
    private BankConnectionRepository connectionRepository;

    @Mock
    private InsightsService insightsService;

    private static BankConnection connection(long id, String name) {
        BankConnection connection = new BankConnection();
        ReflectionTestUtils.setField(connection, "id", id);
        connection.setInstitutionName(name);
        return connection;
    }

    private List<NotificationDraft> syncDrafts(BankConnection... connections) {
        when(connectionRepository.findByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of(connections));
        return new SyncNotificationSource(connectionRepository).drafts(USER_ID, TODAY);
    }

    @Test
    void aFailedSyncWarnsWithTheReasonOncePerDay() {
        BankConnection failed = connection(3, "Nubank");
        failed.setLastSyncError("Pluggy fora do ar");
        failed.setLastSyncAttemptAt(LocalDateTime.of(2026, 9, 21, 6, 0));

        NotificationDraft draft = syncDrafts(failed).get(0);

        assertEquals(NotificationSeverity.WARNING, draft.severity());
        assertEquals("Falha ao sincronizar: Nubank", draft.title());
        assertTrue(draft.message().contains("Pluggy fora do ar. Tente de novo"));
        assertEquals("sync-error:3:2026-09-21", draft.dedupeKey());
    }

    @Test
    void aBankNotSyncedForDaysIsAnInfoNotice() {
        BankConnection stale = connection(4, "Santander");
        stale.setLastSyncedAt(LocalDateTime.of(2026, 9, 17, 6, 0));

        NotificationDraft draft = syncDrafts(stale).get(0);

        assertEquals(NotificationSeverity.INFO, draft.severity());
        assertEquals("Sem sincronizar há 4 dias: Santander", draft.title());
        assertEquals("sync-stale:4:2026-09-17", draft.dedupeKey());
    }

    @Test
    void recentAndNeverSyncedConnectionsAreQuiet() {
        BankConnection recent = connection(5, "Nubank");
        recent.setLastSyncedAt(LocalDateTime.of(2026, 9, 20, 6, 0));
        BankConnection never = connection(6, "Inter");

        assertTrue(syncDrafts(recent, never).isEmpty());
    }

    private List<NotificationDraft> unusualDrafts(UnusualExpense... expenses) {
        InsightsDTO insights = new InsightsDTO("2026-09", null, null, null, List.of(expenses), List.of(), null);
        when(insightsService.insights(USER_ID, null, TODAY)).thenReturn(insights);
        return new UnusualExpenseNotificationSource(insightsService).drafts(USER_ID, TODAY);
    }

    @Test
    void aRecentUnusualExpenseWarns() {
        UnusualExpense expense = new UnusualExpense("Atacadao", "Mercado", TODAY.minusDays(2), new BigDecimal("520.00"), new BigDecimal("103.00"));

        NotificationDraft draft = unusualDrafts(expense).get(0);

        assertEquals(NotificationSeverity.WARNING, draft.severity());
        assertEquals("Gasto fora do padrão: Atacadao", draft.title());
        assertTrue(draft.message().contains("R$ 520,00"));
        assertTrue(draft.message().contains("R$ 103,00"));
        assertEquals("/insights", draft.link());
    }

    @Test
    void anOldUnusualExpenseIsNotNotifiedNow() {
        UnusualExpense old = new UnusualExpense("Atacadao", "Mercado", TODAY.minusDays(10), new BigDecimal("520.00"), new BigDecimal("103.00"));

        assertTrue(unusualDrafts(old).isEmpty());
    }
}
