package com.globo.fintech_backend.Agenda.controller;

import com.globo.fintech_backend.Agenda.dto.AgendaDTO;
import com.globo.fintech_backend.Agenda.service.AgendaService;
import com.globo.fintech_backend.security.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/agenda")
public class AgendaController {

    private final AgendaService service;

    public AgendaController(AgendaService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<AgendaDTO> agenda(@RequestParam(required = false) Integer days) {
        return ResponseEntity.ok(service.agenda(SecurityUtils.getLoggedUserId(), days, LocalDate.now()));
    }
}
