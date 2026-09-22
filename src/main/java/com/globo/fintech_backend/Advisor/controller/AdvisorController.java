package com.globo.fintech_backend.Advisor.controller;

import com.globo.fintech_backend.Advisor.dto.AdviceOverviewDTO;
import com.globo.fintech_backend.Advisor.service.AdvisorService;
import com.globo.fintech_backend.security.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/advisor")
public class AdvisorController {

    private final AdvisorService service;

    public AdvisorController(AdvisorService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<AdviceOverviewDTO> advise() {
        return ResponseEntity.ok(service.advise(SecurityUtils.getLoggedUserId(), LocalDate.now()));
    }
}
