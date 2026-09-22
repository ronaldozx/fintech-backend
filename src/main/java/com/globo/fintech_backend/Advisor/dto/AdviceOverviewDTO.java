package com.globo.fintech_backend.Advisor.dto;

import java.util.List;

public record AdviceOverviewDTO(List<AdviceItemDTO> items, String disclaimer) {}
