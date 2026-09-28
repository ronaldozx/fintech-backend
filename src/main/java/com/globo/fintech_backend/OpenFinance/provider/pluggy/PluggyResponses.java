package com.globo.fintech_backend.OpenFinance.provider.pluggy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.List;

final class PluggyResponses {

    private PluggyResponses() {}

    record AuthRequest(String clientId, String clientSecret) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AuthResponse(String apiKey) {}

    record ConnectTokenRequest(ConnectTokenOptions options) {}

    record ConnectTokenOptions(String clientUserId) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ConnectTokenResponse(String accessToken) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Connector(String name) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Item(String id, String status, String clientUserId, Connector connector) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Page<T>(int page, int totalPages, List<T> results) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CursorPage<T>(List<T> results, String next) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record BankData(BigDecimal overdraftContractedLimit, BigDecimal overdraftUsedLimit) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record CreditData(BigDecimal creditLimit, BigDecimal availableCreditLimit, String balanceDueDate, String brand) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Account(
            String id,
            String type,
            String name,
            BigDecimal balance,
            String currencyCode,
            String number,
            String marketingName,
            BankData bankData,
            CreditData creditData
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Transaction(
            String id,
            String description,
            BigDecimal amount,
            String date,
            String type,
            String status,
            String category
    ) {}
}
