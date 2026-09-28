package com.globo.fintech_backend.Investments.controller;

import com.globo.fintech_backend.Investments.dto.InvestmentsOverviewDTO;
import com.globo.fintech_backend.Investments.service.InvestmentsService;
import com.globo.fintech_backend.security.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/investments")
public class InvestmentsController {

    private final InvestmentsService service;

    public InvestmentsController(InvestmentsService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<InvestmentsOverviewDTO> overview() {
        return ResponseEntity.ok(service.overview(SecurityUtils.getLoggedUserId(), LocalDate.now()));
    }
}
