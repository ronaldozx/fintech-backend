package com.globo.fintech_backend.OpenFinance.accounts;

import com.globo.fintech_backend.security.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/open-finance")
public class AccountsController {

    private final AccountsService service;

    public AccountsController(AccountsService service) {
        this.service = service;
    }

    @GetMapping("/accounts")
    public ResponseEntity<AccountsOverviewDTO> accounts() {
        return ResponseEntity.ok(service.overview(SecurityUtils.getLoggedUserId(), LocalDate.now()));
    }
}
