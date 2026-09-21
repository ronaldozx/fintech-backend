package com.globo.fintech_backend.Privacy.controller;

import com.globo.fintech_backend.Privacy.dto.AccountExportDTO;
import com.globo.fintech_backend.Privacy.dto.DeleteAccountDTO;
import com.globo.fintech_backend.Privacy.dto.DeleteAccountResultDTO;
import com.globo.fintech_backend.Privacy.service.DataPrivacyService;
import com.globo.fintech_backend.security.SecurityUtils;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/account")
public class PrivacyController {

    private static final String EXPORT_FILE = "meus-dados.json";

    private final DataPrivacyService service;

    public PrivacyController(DataPrivacyService service) {
        this.service = service;
    }

    @GetMapping("/export")
    public ResponseEntity<AccountExportDTO> export() {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(EXPORT_FILE).build().toString())
                .body(service.export(SecurityUtils.getLoggedUserId(), LocalDateTime.now()));
    }

    @DeleteMapping
    public ResponseEntity<DeleteAccountResultDTO> delete(@RequestBody DeleteAccountDTO request) {
        return ResponseEntity.ok(service.delete(SecurityUtils.getLoggedUserId(), request.password()));
    }
}
