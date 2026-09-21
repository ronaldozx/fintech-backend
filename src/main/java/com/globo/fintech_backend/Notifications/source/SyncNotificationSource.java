package com.globo.fintech_backend.Notifications.source;

import com.globo.fintech_backend.Notifications.dto.NotificationDraft;
import com.globo.fintech_backend.Notifications.entity.NotificationSeverity;
import com.globo.fintech_backend.OpenFinance.connection.BankConnection;
import com.globo.fintech_backend.OpenFinance.connection.BankConnectionRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Component
public class SyncNotificationSource implements NotificationSource {

    static final int STALE_DAYS = 3;
    private static final String TYPE = "SYNC";
    private static final String LINK = "/home";

    private final BankConnectionRepository connectionRepository;

    public SyncNotificationSource(BankConnectionRepository connectionRepository) {
        this.connectionRepository = connectionRepository;
    }

    @Override
    public List<NotificationDraft> drafts(Long userId, LocalDate today) {
        List<NotificationDraft> drafts = new ArrayList<>();

        for (BankConnection connection : connectionRepository.findByUserIdOrderByCreatedAtDesc(userId)) {
            String bank = connection.getInstitutionName() == null ? "banco" : connection.getInstitutionName();

            if (connection.getLastSyncError() != null && connection.getLastSyncAttemptAt() != null) {
                drafts.add(new NotificationDraft(
                        TYPE,
                        NotificationSeverity.WARNING,
                        "Falha ao sincronizar: " + bank,
                        "Não consegui buscar os dados novos. " + sentence(connection.getLastSyncError())
                                + " Tente de novo em Bancos > Sincronizar.",
                        LINK,
                        "sync-error:" + connection.getId() + ":" + connection.getLastSyncAttemptAt().toLocalDate()));
            } else if (connection.getLastSyncedAt() != null) {
                long days = ChronoUnit.DAYS.between(connection.getLastSyncedAt().toLocalDate(), today);
                if (days >= STALE_DAYS) {
                    drafts.add(new NotificationDraft(
                            TYPE,
                            NotificationSeverity.INFO,
                            "Sem sincronizar há " + days + " dias: " + bank,
                            "Os saldos e as transações deste banco podem estar desatualizados.",
                            LINK,
                            "sync-stale:" + connection.getId() + ":" + connection.getLastSyncedAt().toLocalDate()));
                }
            }
        }

        return drafts;
    }

    private static String sentence(String text) {
        String trimmed = text.trim();
        return trimmed.endsWith(".") ? trimmed : trimmed + ".";
    }
}
