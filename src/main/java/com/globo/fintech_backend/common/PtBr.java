package com.globo.fintech_backend.common;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class PtBr {

    private static final Locale LOCALE = Locale.forLanguageTag("pt-BR");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", LOCALE);
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd/MM", LOCALE);

    private PtBr() {
    }

    public static String money(BigDecimal amount) {
        return NumberFormat.getCurrencyInstance(LOCALE).format(amount).replace(' ', ' ');
    }

    public static String month(YearMonth month) {
        return month.format(MONTH);
    }

    public static String dayMonth(java.time.LocalDate date) {
        return date.format(DAY_MONTH);
    }
}
