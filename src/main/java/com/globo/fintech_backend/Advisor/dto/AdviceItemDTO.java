package com.globo.fintech_backend.Advisor.dto;

public record AdviceItemDTO(
        AdviceType type,
        AdviceSeverity severity,
        String category,
        String title,
        String message,
        String link
) {}
