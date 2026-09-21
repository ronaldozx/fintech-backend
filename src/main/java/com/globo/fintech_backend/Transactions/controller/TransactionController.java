package com.globo.fintech_backend.Transactions.controller;
import com.globo.fintech_backend.Transactions.dto.CategorySummaryDTO;
import com.globo.fintech_backend.Transactions.dto.DailySummaryDTO;
import com.globo.fintech_backend.Transactions.dto.MonthlySummaryDTO;
import com.globo.fintech_backend.Transactions.dto.TransactionDashboardDTO;
import com.globo.fintech_backend.Transactions.dto.ReconcileResultDTO;
import com.globo.fintech_backend.Transactions.dto.TransactionCreateDTO;
import com.globo.fintech_backend.Transactions.dto.TransactionRowDTO;
import com.globo.fintech_backend.Transactions.dto.TransactionUpdateDTO;
import com.globo.fintech_backend.Transactions.service.OwnTransferReconciler;
import com.globo.fintech_backend.Transactions.service.TransactionEditService;
import com.globo.fintech_backend.Transactions.service.TransactionService;
import com.globo.fintech_backend.security.SecurityUtils;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/transaction")
public class TransactionController {

    private final TransactionService transactionService;
    private final TransactionEditService editService;
    private final OwnTransferReconciler reconciler;

    public TransactionController(TransactionService transactionService,
                                 TransactionEditService editService,
                                 OwnTransferReconciler reconciler){
        this.transactionService = transactionService;
        this.editService = editService;
        this.reconciler = reconciler;
    }

    @PostMapping
    public ResponseEntity<TransactionRowDTO> create(@RequestBody TransactionCreateDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(editService.create(SecurityUtils.getLoggedUserId(), request, LocalDate.now()));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<TransactionRowDTO> update(@PathVariable Long id, @RequestBody TransactionUpdateDTO request) {
        return ResponseEntity.ok(editService.update(SecurityUtils.getLoggedUserId(), id, request, LocalDate.now()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        editService.delete(SecurityUtils.getLoggedUserId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reconcile-transfers")
    public ResponseEntity<ReconcileResultDTO> reconcileTransfers() {
        return ResponseEntity.ok(new ReconcileResultDTO(reconciler.reconcile(SecurityUtils.getLoggedUserId())));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<TransactionDashboardDTO> getDashboardData(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
        Pageable pageable
    ){
        Long userId = SecurityUtils.getLoggedUserId();
        return ResponseEntity.ok(transactionService.getDashboardData(userId, startDate, endDate, pageable));
    }

    @GetMapping("/summary/categories")
    public ResponseEntity<List<CategorySummaryDTO>> getExpensesByCategory(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ){
        Long userId = SecurityUtils.getLoggedUserId();
        return ResponseEntity.ok(transactionService.getExpensesByCategory(userId, startDate, endDate));
    }

    @GetMapping("/summary/daily")
    public ResponseEntity<List<DailySummaryDTO>> getDailySummary(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ){
        Long userId = SecurityUtils.getLoggedUserId();
        return ResponseEntity.ok(transactionService.getDailySummary(userId, startDate, endDate));
    }

    @GetMapping("/summary/monthly")
    public ResponseEntity<List<MonthlySummaryDTO>> getMonthlySummary(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ){
        Long userId = SecurityUtils.getLoggedUserId();
        return ResponseEntity.ok(transactionService.getMonthlySummary(userId, startDate, endDate));
    }
}
