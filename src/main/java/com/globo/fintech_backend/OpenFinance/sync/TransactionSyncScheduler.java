package com.globo.fintech_backend.OpenFinance.sync;

import com.globo.fintech_backend.OpenFinance.connection.BankConnection;
import com.globo.fintech_backend.OpenFinance.connection.BankConnectionRepository;
import com.globo.fintech_backend.OpenFinance.provider.pluggy.PluggyProperties;
import com.globo.fintech_backend.Transactions.service.OwnTransferReconciler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

@Component
public class TransactionSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(TransactionSyncScheduler.class);

    private final BankConnectionRepository connectionRepository;
    private final TransactionSyncService syncService;
    private final PluggyProperties properties;
    private final OwnTransferReconciler reconciler;

    public TransactionSyncScheduler(BankConnectionRepository connectionRepository,
                                    TransactionSyncService syncService,
                                    PluggyProperties properties,
                                    OwnTransferReconciler reconciler) {
        this.connectionRepository = connectionRepository;
        this.syncService = syncService;
        this.properties = properties;
        this.reconciler = reconciler;
    }

    @Scheduled(cron = "${open-finance.sync.cron:0 0 */6 * * *}")
    public void syncAll() {
        if (!properties.isConfigured()) {
            return;
        }

        Set<Long> syncedUsers = new LinkedHashSet<>();

        for (BankConnection connection : connectionRepository.findAll()) {
            try {
                int imported = syncService.syncConnection(connection);
                syncedUsers.add(connection.getUser().getId());
                log.info("Synced connection {}: {} new transactions", connection.getId(), imported);
            } catch (RuntimeException e) {
                log.warn("Sync failed for connection {}: {}", connection.getId(), e.getMessage());
            }
        }

        for (Long userId : syncedUsers) {
            try {
                int pairs = reconciler.reconcile(userId);
                log.info("Reconciled {} own transfers for user {}", pairs, userId);
            } catch (RuntimeException e) {
                log.warn("Transfer reconciliation failed for user {}: {}", userId, e.getMessage());
            }
        }
    }
}
