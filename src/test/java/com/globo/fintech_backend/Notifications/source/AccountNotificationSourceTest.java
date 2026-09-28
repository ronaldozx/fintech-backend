package com.globo.fintech_backend.Notifications.source;

import com.globo.fintech_backend.Notifications.dto.NotificationDraft;
import com.globo.fintech_backend.Notifications.entity.NotificationSeverity;
import com.globo.fintech_backend.OpenFinance.accounts.AccountDTO;
import com.globo.fintech_backend.OpenFinance.accounts.AccountsOverviewDTO;
import com.globo.fintech_backend.OpenFinance.accounts.AccountsService;
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
class AccountNotificationSourceTest {

    private static final Long USER_ID = 7L;
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 21);

    @Mock
    private AccountsService accountsService;

    private static AccountDTO card(String balance, String limit, String available, LocalDate due) {
        return new AccountDTO(1L, "Nubank", "Cartão Gold", "CREDIT", "•••• 1234", new BigDecimal(balance), "BRL",
                limit == null ? null : new BigDecimal(limit), available == null ? null : new BigDecimal(available), due, null, null);
    }

    private static AccountDTO bank(String limit, String used) {
        return new AccountDTO(2L, "Santander", "Conta", "BANK", "•••• 9999", new BigDecimal("-100"), "BRL", null, null, null,
                limit == null ? null : new BigDecimal(limit), used == null ? null : new BigDecimal(used));
    }

    private List<NotificationDraft> drafts(AccountDTO... accounts) {
        when(accountsService.overview(USER_ID, TODAY))
                .thenReturn(new AccountsOverviewDTO(List.of(accounts), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of()));
        return new AccountNotificationSource(accountsService).drafts(USER_ID, TODAY);
    }

    @Test
    void aBillDueInThreeDaysWarns() {
        NotificationDraft draft = drafts(card("1200.00", null, null, TODAY.plusDays(3))).get(0);

        assertEquals(NotificationSeverity.WARNING, draft.severity());
        assertEquals("Fatura vence em 3 dias: Cartão Gold", draft.title());
        assertTrue(draft.message().contains("R$ 1.200,00"));
        assertTrue(draft.message().contains("24/09"));
        assertEquals("due:1:Cartão Gold:2026-09-24", draft.dedupeKey());
    }

    @Test
    void aBillDueTodayOrTomorrowIsCritical() {
        assertEquals("Fatura vence hoje: Cartão Gold", drafts(card("50", null, null, TODAY)).get(0).title());
        NotificationDraft tomorrow = drafts(card("50", null, null, TODAY.plusDays(1))).get(0);
        assertEquals("Fatura vence amanhã: Cartão Gold", tomorrow.title());
        assertEquals(NotificationSeverity.CRITICAL, tomorrow.severity());
    }

    @Test
    void farAwayPastOrPaidBillsAreIgnored() {
        assertTrue(drafts(card("1200", null, null, TODAY.plusDays(6))).isEmpty());
        assertTrue(drafts(card("1200", null, null, TODAY.minusDays(1))).isEmpty());
        assertTrue(drafts(card("0", null, null, TODAY.plusDays(2))).isEmpty());
        assertTrue(drafts(card("1200", null, null, null)).isEmpty());
    }

    @Test
    void aCardNearItsLimitWarnsAndOneWithoutLimitLeftIsCritical() {
        NotificationDraft near = drafts(card("100", "1000", "150", null)).get(0);
        assertEquals(NotificationSeverity.WARNING, near.severity());
        assertEquals("Cartão perto do limite: Cartão Gold", near.title());
        assertTrue(near.message().contains("85%"));
        assertEquals("climit:1:Cartão Gold:2026-09:80", near.dedupeKey());

        NotificationDraft exhausted = drafts(card("100", "1000", "0", null)).get(0);
        assertEquals(NotificationSeverity.CRITICAL, exhausted.severity());
        assertEquals("climit:1:Cartão Gold:2026-09:100", exhausted.dedupeKey());
    }

    @Test
    void aCardWithRoomLeftProducesNothing() {
        assertTrue(drafts(card("100", "1000", "700", null)).isEmpty());
    }

    @Test
    void overdraftUseWarnsFromEightyPercent() {
        assertTrue(drafts(bank("1500", "1000")).isEmpty());

        NotificationDraft warning = drafts(bank("1500", "1300")).get(0);
        assertEquals(NotificationSeverity.WARNING, warning.severity());
        assertEquals("Cheque especial em uso: Santander", warning.title());
        assertEquals("overdraft:2:2026-09:80", warning.dedupeKey());

        NotificationDraft full = drafts(bank("1500", "1500")).get(0);
        assertEquals(NotificationSeverity.CRITICAL, full.severity());
        assertEquals("overdraft:2:2026-09:100", full.dedupeKey());
    }

    @Test
    void accountsWithoutOverdraftProduceNothing() {
        assertTrue(drafts(bank(null, null)).isEmpty());
        assertTrue(drafts(bank("0", "0")).isEmpty());
    }
}
