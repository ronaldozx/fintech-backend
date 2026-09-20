package com.globo.fintech_backend.OpenFinance.provider;

public record ProviderItem(
        String id,
        String status,
        String institutionName,
        String clientUserId
) {}
