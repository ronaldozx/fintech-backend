package com.globo.fintech_backend.Insights.controller;

import com.globo.fintech_backend.Insights.dto.InsightsDTO;
import com.globo.fintech_backend.Insights.service.InsightsService;
import com.globo.fintech_backend.security.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/insights")
public class InsightsController {

    private final InsightsService service;

    public InsightsController(InsightsService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<InsightsDTO> insights(@RequestParam(required = false) String month) {
        return ResponseEntity.ok(service.insights(SecurityUtils.getLoggedUserId(), month, LocalDate.now()));
    }
}
