package com.globo.fintech_backend.Budgets.controller;

import com.globo.fintech_backend.Budgets.dto.BudgetDTO;
import com.globo.fintech_backend.Budgets.dto.BudgetRequestDTO;
import com.globo.fintech_backend.Budgets.dto.BudgetUpdateDTO;
import com.globo.fintech_backend.Budgets.dto.BudgetsOverviewDTO;
import com.globo.fintech_backend.Budgets.service.BudgetService;
import com.globo.fintech_backend.security.SecurityUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;

@RestController
@RequestMapping("/budgets")
public class BudgetController {

    private final BudgetService service;

    public BudgetController(BudgetService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<BudgetsOverviewDTO> overview(@RequestParam(required = false) String month) {
        return ResponseEntity.ok(service.overview(SecurityUtils.getLoggedUserId(), month, YearMonth.now()));
    }

    @PostMapping
    public ResponseEntity<BudgetDTO> create(@RequestBody BudgetRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(SecurityUtils.getLoggedUserId(), request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<BudgetDTO> update(@PathVariable Long id, @RequestBody BudgetUpdateDTO request) {
        return ResponseEntity.ok(service.update(SecurityUtils.getLoggedUserId(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(SecurityUtils.getLoggedUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
