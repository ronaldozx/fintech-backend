package com.globo.fintech_backend.OpenFinance.sync;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class CardBillPaymentDetector {

    private static final Pattern BILL_PAYMENT = Pattern.compile(
            "^(pagamento|pgto|pagto|pag)( (de|da|do))?( a)? fatura( (de|do|da))?( (cartao( de credito)?|credito|nubank))?$");

    public boolean matches(String description) {
        if (description == null || description.isBlank()) {
            return false;
        }
        return BILL_PAYMENT.matcher(normalize(description)).matches();
    }

    private static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
