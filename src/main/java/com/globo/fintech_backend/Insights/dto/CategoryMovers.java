package com.globo.fintech_backend.Insights.dto;

import java.util.List;

public record CategoryMovers(List<CategoryChange> increases, List<CategoryChange> decreases) {}
