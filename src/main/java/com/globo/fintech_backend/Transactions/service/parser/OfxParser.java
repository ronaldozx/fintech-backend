package com.globo.fintech_backend.Transactions.service.parser;


import com.globo.fintech_backend.Transactions.dto.TransactionDTO;
import com.globo.fintech_backend.Transactions.enums.PaymentMethod;
import com.globo.fintech_backend.Transactions.enums.TransactionType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class OfxParser {

    public List<TransactionDTO> parse(MultipartFile file) throws Exception {
        List<TransactionDTO> transactions = new ArrayList<>();
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream())
        );

        String description = null;
        BigDecimal amount = null;
        LocalDate date = null;
        TransactionType type = null;
        PaymentMethod paymentMethod = null;

        String line;
        while ((line = reader.readLine()) != null) {
            line = line.trim();

            if (line.startsWith("<MEMO>")) {
                description = extractValue(line, "MEMO");
            } else if (line.startsWith("<TRNAMT>")) {
                String value = extractValue(line, "TRNAMT");
                amount = new BigDecimal(value.replace(",", "."));
                type = (amount.compareTo(BigDecimal.ZERO) < 0)
                        ? TransactionType.EXPENSE
                        : TransactionType.INCOME;
            } else if (line.startsWith("<DTPOSTED>")) {
                String rawDate = extractValue(line, "DTPOSTED");
                rawDate = rawDate.substring(0, 8); // pega só yyyyMMdd
                date = LocalDate.parse(rawDate, DateTimeFormatter.ofPattern("yyyyMMdd"));
            } else if (line.startsWith("<TRNTYPE>")) {
                String rawType = extractValue(line, "TRNTYPE");
                paymentMethod = rawType.equalsIgnoreCase("CREDIT")
                        ? PaymentMethod.CREDIT
                        : PaymentMethod.DEBIT;
            } else if (line.startsWith("</STMTTRN>")) {
                if (description != null && amount != null && date != null && paymentMethod != null) {
                    transactions.add(new TransactionDTO(description, amount, date, type, paymentMethod, null));
                }
                description = null;
                amount = null;
                date = null;
                paymentMethod = null;
            }
        }

        return transactions;
    }

    private String extractValue(String line, String tag) {
        return line.replace("<" + tag + ">", "").replace("</" + tag + ">", "").trim();
    }
}