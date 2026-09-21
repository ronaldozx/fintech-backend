package com.globo.fintech_backend.Transactions.controller;

import com.globo.fintech_backend.Transactions.dto.TransactionSearchDTO;
import com.globo.fintech_backend.Transactions.enums.PaymentMethod;
import com.globo.fintech_backend.Transactions.enums.TransactionType;
import com.globo.fintech_backend.Transactions.query.TransactionFilter;
import com.globo.fintech_backend.Transactions.service.TransactionSearchService;
import com.globo.fintech_backend.security.SecurityUtils;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/transaction")
public class TransactionSearchController {

    private static final MediaType CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final TransactionSearchService service;

    public TransactionSearchController(TransactionSearchService service) {
        this.service = service;
    }

    @GetMapping("/search")
    public ResponseEntity<TransactionSearchDTO> search(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) PaymentMethod paymentMethod,
            @RequestParam(required = false) Boolean neutral,
            Pageable pageable
    ) {
        TransactionFilter filter = new TransactionFilter(
                SecurityUtils.getLoggedUserId(), startDate, endDate, q, category, type, paymentMethod, neutral);
        return ResponseEntity.ok(service.search(filter, pageable));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) TransactionType type,
            @RequestParam(required = false) PaymentMethod paymentMethod,
            @RequestParam(required = false) Boolean neutral,
            Pageable pageable
    ) {
        TransactionFilter filter = new TransactionFilter(
                SecurityUtils.getLoggedUserId(), startDate, endDate, q, category, type, paymentMethod, neutral);
        byte[] body = service.exportCsv(filter, pageable.getSort()).getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .contentType(CSV)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("transacoes.csv").build().toString())
                .body(body);
    }

    @GetMapping("/categories")
    public ResponseEntity<List<String>> categories() {
        return ResponseEntity.ok(service.categories(SecurityUtils.getLoggedUserId()));
    }
}
