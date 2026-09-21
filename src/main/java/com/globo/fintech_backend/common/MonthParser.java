package com.globo.fintech_backend.common;

import com.globo.fintech_backend.exception.BadRequestException;

import java.time.DateTimeException;
import java.time.YearMonth;

public final class MonthParser {

    private MonthParser() {
    }

    public static YearMonth parse(String text, YearMonth fallback) {
        if (text == null || text.isBlank()) {
            return fallback;
        }
        try {
            return YearMonth.parse(text.trim());
        } catch (DateTimeException e) {
            throw new BadRequestException("Mês inválido, use o formato AAAA-MM");
        }
    }
}
