package com.globo.fintech_backend.OpenFinance.sync;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CardBillPaymentDetectorTest {

    private final CardBillPaymentDetector detector = new CardBillPaymentDetector();

    @ParameterizedTest
    @ValueSource(strings = {
            "PAGAMENTO DE FATURA",
            "Pagamento de fatura",
            "  pagamento   de   fatura  ",
            "PGTO FATURA",
            "PAGTO FATURA CARTAO",
            "Pagamento da fatura do cartão de crédito",
            "PAGAMENTO FATURA NUBANK",
            "Pagamento de fatura - cartão"
    })
    void recognizesCardBillPayments(String description) {
        assertTrue(detector.matches(description), description);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "PAGAMENTO DE FATURA ENEL",
            "Fatura de internet",
            "PAGAMENTO DE BOLETO",
            "Pagamento recebido de fatura de cliente",
            "PIX ENVIADO Maria",
            "Compra no cartão",
            "FATURA"
    })
    void ignoresOtherBillsAndRegularPurchases(String description) {
        assertFalse(detector.matches(description), description);
    }

    @Test
    void nullAndBlankDoNotMatch() {
        assertFalse(detector.matches(null));
        assertFalse(detector.matches("   "));
    }
}
