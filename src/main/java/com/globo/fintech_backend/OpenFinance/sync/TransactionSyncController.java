package com.globo.fintech_backend.OpenFinance.sync;

import com.globo.fintech_backend.Transactions.service.OwnTransferReconciler;
import com.globo.fintech_backend.security.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/open-finance")
public class TransactionSyncController {

    private final TransactionSyncService syncService;
    private final OwnTransferReconciler reconciler;

    public TransactionSyncController(TransactionSyncService syncService, OwnTransferReconciler reconciler) {
        this.syncService = syncService;
        this.reconciler = reconciler;
    }

    @PostMapping("/sync")
    public ResponseEntity<SyncResultDTO> sync() {
        Long userId = SecurityUtils.getLoggedUserId();
        SyncResultDTO result = syncService.syncUser(userId);
        return ResponseEntity.ok(result.withTransferPairs(reconciler.reconcile(userId)));
    }
}
