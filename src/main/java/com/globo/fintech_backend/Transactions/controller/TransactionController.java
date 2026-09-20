package com.globo.fintech_backend.Transactions.controller;
import com.globo.fintech_backend.Transactions.dto.CategorySummaryDTO;
import com.globo.fintech_backend.Transactions.dto.MonthlySummaryDTO;
import com.globo.fintech_backend.Transactions.dto.TransactionDashboardDTO;
import com.globo.fintech_backend.Transactions.service.TransactionService;
import com.globo.fintech_backend.security.SecurityUtils;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/transaction")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService){
        this.transactionService = transactionService;
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

    @GetMapping("/summary/monthly")
    public ResponseEntity<List<MonthlySummaryDTO>> getMonthlySummary(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ){
        Long userId = SecurityUtils.getLoggedUserId();
        return ResponseEntity.ok(transactionService.getMonthlySummary(userId, startDate, endDate));
    }
}
