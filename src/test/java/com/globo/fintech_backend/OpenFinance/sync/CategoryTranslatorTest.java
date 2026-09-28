package com.globo.fintech_backend.OpenFinance.sync;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CategoryTranslatorTest {

    private final CategoryTranslator translator = new CategoryTranslator();

    @ParameterizedTest
    @CsvSource({
            "Salary, Salário",
            "Groceries, Supermercado",
            "Shopping, Compras",
            "Food delivery, Delivery de alimentos",
            "Transfer - PIX, Transferência - PIX",
            "Credit card payment, Pagamento de cartão de crédito"
    })
    void translatesTheProviderCategoriesToPortuguese(String english, String portuguese) {
        assertEquals(portuguese, translator.translate(english));
    }

    @Test
    void ignoresSurroundingWhitespaceWhenTranslating() {
        assertEquals("Salário", translator.translate("  Salary "));
    }

    @Test
    void unknownCategoriesAreReturnedUntouched() {
        assertEquals("Algo Novo", translator.translate("Algo Novo"));
    }

    @Test
    void nullAndBlankPassThrough() {
        assertNull(translator.translate(null));
        assertEquals(" ", translator.translate(" "));
    }

    @Test
    void cardPaymentsAndTransfersBetweenOwnAccountsAreNeutral() {
        assertTrue(translator.isNeutral("Credit card payment"));
        assertTrue(translator.isNeutral("Same person transfer"));
        assertTrue(translator.isNeutral("Same person transfer - PIX"));
        assertTrue(translator.isNeutral("Same person transfer - TED"));
    }

    @Test
    void regularCategoriesAreNotNeutral() {
        assertFalse(translator.isNeutral("Groceries"));
        assertFalse(translator.isNeutral("Transfer - PIX"));
        assertFalse(translator.isNeutral("Third party transfer - PIX"));
        assertFalse(translator.isNeutral(null));
    }
}
