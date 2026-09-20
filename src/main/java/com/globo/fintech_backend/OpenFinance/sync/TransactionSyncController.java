package com.globo.fintech_backend.OpenFinance.sync;

import com.globo.fintech_backend.security.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/open-finance")
public class TransactionSyncController {

    private final TransactionSyncService syncService;

    public TransactionSyncController(TransactionSyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping("/sync")
    public ResponseEntity<SyncResultDTO> sync() {
        return ResponseEntity.ok(syncService.syncUser(SecurityUtils.getLoggedUserId()));
    }
}
