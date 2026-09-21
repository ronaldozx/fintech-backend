package com.globo.fintech_backend.Notifications.source;

import com.globo.fintech_backend.Notifications.dto.NotificationDraft;
import com.globo.fintech_backend.Notifications.entity.NotificationSeverity;
import com.globo.fintech_backend.OpenFinance.accounts.AccountDTO;
import com.globo.fintech_backend.OpenFinance.accounts.AccountsService;
import com.globo.fintech_backend.common.PtBr;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Component
public class AccountNotificationSource implements NotificationSource {

    static final int DUE_SOON_DAYS = 5;
    static final int WARNING_PERCENT = 80;
    static final int LIMIT_PERCENT = 100;
    private static final String TYPE = "ACCOUNT";
    private static final String LINK = "/contas";
    private static final String CREDIT = "CREDIT";

    private final AccountsService accountsService;

    public AccountNotificationSource(AccountsService accountsService) {
        this.accountsService = accountsService;
    }

    @Override
    public List<NotificationDraft> drafts(Long userId, LocalDate today) {
        List<NotificationDraft> drafts = new ArrayList<>();
        String monthKey = YearMonth.from(today).toString();

        for (AccountDTO account : accountsService.overview(userId, today).accounts()) {
            if (CREDIT.equals(account.type())) {
                dueSoon(account, today).ifPresent(drafts::add);
                creditLimit(account, monthKey).ifPresent(drafts::add);
            } else {
                overdraft(account, monthKey).ifPresent(drafts::add);
            }
        }

        return drafts;
    }

    private static java.util.Optional<NotificationDraft> dueSoon(AccountDTO account, LocalDate today) {
        if (account.dueDate() == null || account.balance() == null || account.balance().signum() <= 0) {
            return java.util.Optional.empty();
        }

        long days = ChronoUnit.DAYS.between(today, account.dueDate());
        if (days < 0 || days > DUE_SOON_DAYS) {
            return java.util.Optional.empty();
        }

        String when = days == 0 ? "hoje" : days == 1 ? "amanhã" : "em " + days + " dias";

        return java.util.Optional.of(new NotificationDraft(
                TYPE,
                days <= 1 ? NotificationSeverity.CRITICAL : NotificationSeverity.WARNING,
                "Fatura vence " + when + ": " + account.name(),
                "Fatura de " + PtBr.money(account.balance()) + " do " + account.institution()
                        + " vence em " + PtBr.dayMonth(account.dueDate()) + ".",
                LINK,
                "due:" + account.connectionId() + ":" + account.name() + ":" + account.dueDate()));
    }

    private static java.util.Optional<NotificationDraft> creditLimit(AccountDTO account, String monthKey) {
        if (account.creditLimit() == null || account.creditLimit().signum() <= 0) {
            return java.util.Optional.empty();
        }

        BigDecimal available = account.availableCredit() == null ? account.creditLimit() : account.availableCredit();
        BigDecimal used = account.creditLimit().subtract(available).max(BigDecimal.ZERO);
        int percent = percent(used, account.creditLimit());
        int level = level(percent);
        if (level == 0) {
            return java.util.Optional.empty();
        }

        boolean exhausted = level == LIMIT_PERCENT;
        return java.util.Optional.of(new NotificationDraft(
                TYPE,
                exhausted ? NotificationSeverity.CRITICAL : NotificationSeverity.WARNING,
                (exhausted ? "Limite do cartão esgotado: " : "Cartão perto do limite: ") + account.name(),
                "Você usou " + PtBr.money(used) + " de " + PtBr.money(account.creditLimit()) + " (" + percent + "%) no "
                        + account.institution() + ".",
                LINK,
                "climit:" + account.connectionId() + ":" + account.name() + ":" + monthKey + ":" + level));
    }

    private static java.util.Optional<NotificationDraft> overdraft(AccountDTO account, String monthKey) {
        if (account.overdraftLimit() == null || account.overdraftLimit().signum() <= 0
                || account.overdraftUsed() == null || account.overdraftUsed().signum() <= 0) {
            return java.util.Optional.empty();
        }

        int percent = percent(account.overdraftUsed(), account.overdraftLimit());
        int level = level(percent);
        if (level == 0) {
            return java.util.Optional.empty();
        }

        boolean exhausted = level == LIMIT_PERCENT;
        return java.util.Optional.of(new NotificationDraft(
                TYPE,
                exhausted ? NotificationSeverity.CRITICAL : NotificationSeverity.WARNING,
                (exhausted ? "Cheque especial no limite: " : "Cheque especial em uso: ") + account.institution(),
                "Você está usando " + PtBr.money(account.overdraftUsed()) + " de " + PtBr.money(account.overdraftLimit())
                        + " (" + percent + "%) do cheque especial. Ele costuma ter juros altos.",
                LINK,
                "overdraft:" + account.connectionId() + ":" + monthKey + ":" + level));
    }

    private static int percent(BigDecimal used, BigDecimal limit) {
        return used.multiply(BigDecimal.valueOf(100)).divide(limit, 0, RoundingMode.DOWN).min(BigDecimal.valueOf(999)).intValue();
    }

    private static int level(int percent) {
        if (percent >= LIMIT_PERCENT) {
            return LIMIT_PERCENT;
        }
        return percent >= WARNING_PERCENT ? WARNING_PERCENT : 0;
    }
}
