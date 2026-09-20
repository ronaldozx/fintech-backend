package com.globo.fintech_backend.OpenFinance.connection;

import com.globo.fintech_backend.OpenFinance.connection.dto.BankConnectionDTO;
import com.globo.fintech_backend.OpenFinance.connection.dto.ConnectTokenDTO;
import com.globo.fintech_backend.OpenFinance.connection.dto.RegisterConnectionDTO;
import com.globo.fintech_backend.security.SecurityUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/open-finance")
public class BankConnectionController {

    private final BankConnectionService service;

    public BankConnectionController(BankConnectionService service) {
        this.service = service;
    }

    @PostMapping("/connect-token")
    public ResponseEntity<ConnectTokenDTO> createConnectToken() {
        return ResponseEntity.ok(service.createConnectToken(SecurityUtils.getLoggedUserId()));
    }

    @PostMapping("/connections")
    public ResponseEntity<BankConnectionDTO> register(@RequestBody RegisterConnectionDTO dto) {
        return ResponseEntity.status(201).body(service.register(SecurityUtils.getLoggedUserId(), dto.itemId()));
    }

    @GetMapping("/connections")
    public ResponseEntity<List<BankConnectionDTO>> list() {
        return ResponseEntity.ok(service.list(SecurityUtils.getLoggedUserId()));
    }

    @DeleteMapping("/connections/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(SecurityUtils.getLoggedUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
