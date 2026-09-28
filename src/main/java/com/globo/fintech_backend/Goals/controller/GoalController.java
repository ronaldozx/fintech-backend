package com.globo.fintech_backend.Goals.controller;

import com.globo.fintech_backend.Goals.dto.ContributionRequestDTO;
import com.globo.fintech_backend.Goals.dto.GoalDTO;
import com.globo.fintech_backend.Goals.dto.GoalRequestDTO;
import com.globo.fintech_backend.Goals.dto.GoalsOverviewDTO;
import com.globo.fintech_backend.Goals.service.GoalService;
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
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/goals")
public class GoalController {

    private final GoalService service;

    public GoalController(GoalService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<GoalsOverviewDTO> overview() {
        return ResponseEntity.ok(service.overview(SecurityUtils.getLoggedUserId(), LocalDate.now()));
    }

    @PostMapping
    public ResponseEntity<GoalDTO> create(@RequestBody GoalRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(SecurityUtils.getLoggedUserId(), request, LocalDate.now()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<GoalDTO> update(@PathVariable Long id, @RequestBody GoalRequestDTO request) {
        return ResponseEntity.ok(service.update(SecurityUtils.getLoggedUserId(), id, request, LocalDate.now()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(SecurityUtils.getLoggedUserId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/contributions")
    public ResponseEntity<GoalDTO> contribute(@PathVariable Long id, @RequestBody ContributionRequestDTO request) {
        return ResponseEntity.ok(service.contribute(SecurityUtils.getLoggedUserId(), id, request.amount(), LocalDate.now()));
    }
}
