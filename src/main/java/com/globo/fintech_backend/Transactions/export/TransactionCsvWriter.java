package com.globo.fintech_backend.Transactions.export;

import com.globo.fintech_backend.Transactions.dto.TransactionRowDTO;
import com.globo.fintech_backend.Transactions.enums.PaymentMethod;
import com.globo.fintech_backend.Transactions.enums.TransactionType;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class TransactionCsvWriter {

    private static final String SEPARATOR = ";";
    private static final String NEWLINE = "\r\n";
    private static final String BOM = "﻿";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String HEADER = String.join(SEPARATOR,
            "Data", "Descrição", "Categoria", "Tipo", "Forma de pagamento", "Valor", "Conta nos totais");

    public String write(List<TransactionRowDTO> rows) {
        StringBuilder csv = new StringBuilder(BOM).append(HEADER).append(NEWLINE);

        for (TransactionRowDTO row : rows) {
            csv.append(String.join(SEPARATOR,
                            row.date().format(DATE_FORMAT),
                            escape(row.description()),
                            escape(row.category() == null ? "Outros" : row.category()),
                            row.type() == TransactionType.INCOME ? "Receita" : "Despesa",
                            row.paymentMethod() == PaymentMethod.CREDIT ? "Crédito" : "Débito",
                            row.amount().toPlainString().replace('.', ','),
                            row.neutral() ? "Não" : "Sim"))
                    .append(NEWLINE);
        }

        return csv.toString();
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.replace("\r", " ").replace("\n", " ");
        String guarded = startsLikeFormula(cleaned) ? "'" + cleaned : cleaned;
        boolean needsQuotes = guarded.contains(SEPARATOR) || guarded.contains("\"");
        String escaped = guarded.replace("\"", "\"\"");
        return needsQuotes ? "\"" + escaped + "\"" : escaped;
    }

    private static boolean startsLikeFormula(String value) {
        if (value.isEmpty()) {
            return false;
        }
        char first = value.charAt(0);
        return first == '=' || first == '+' || first == '-' || first == '@';
    }
}
