package com.globo.fintech_backend.OpenFinance.sync;

import com.globo.fintech_backend.OpenFinance.connection.BankConnection;
import com.globo.fintech_backend.OpenFinance.connection.BankConnectionRepository;
import com.globo.fintech_backend.OpenFinance.provider.pluggy.PluggyProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TransactionSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(TransactionSyncScheduler.class);

    private final BankConnectionRepository connectionRepository;
    private final TransactionSyncService syncService;
    private final PluggyProperties properties;

    public TransactionSyncScheduler(BankConnectionRepository connectionRepository,
                                    TransactionSyncService syncService,
                                    PluggyProperties properties) {
        this.connectionRepository = connectionRepository;
        this.syncService = syncService;
        this.properties = properties;
    }

    @Scheduled(cron = "${open-finance.sync.cron:0 0 */6 * * *}")
    public void syncAll() {
        if (!properties.isConfigured()) {
            return;
        }

        for (BankConnection connection : connectionRepository.findAll()) {
            try {
                int imported = syncService.syncConnection(connection);
                log.info("Synced connection {}: {} new transactions", connection.getId(), imported);
            } catch (RuntimeException e) {
                log.warn("Sync failed for connection {}: {}", connection.getId(), e.getMessage());
            }
        }
    }
}
