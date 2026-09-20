package com.globo.fintech_backend.OpenFinance.sync;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CategoryClassifierTest {

    private final CategoryClassifier classifier = new CategoryClassifier();

    @ParameterizedTest
    @CsvSource({
            "Supermercado Extra Compra, Alimentação",
            "IFOOD *PEDIDO, Alimentação",
            "Uber Trip, Transporte",
            "Posto Shell, Transporte",
            "Farmácia Pague Menos, Saúde",
            "NETFLIX.COM, Lazer",
            "Aluguel apartamento, Moradia",
            "Pagamento de salario, Renda",
            "Pix enviado Joao, Transferências",
            "Loja qualquer, Outros"
    })
    void classifiesByKeywordIgnoringCaseAndAccents(String description, String expected) {
        assertEquals(expected, classifier.classify(description));
    }

    @Test
    void doesNotMatchKeywordsInsideOtherWords() {
        assertEquals("Outros", classifier.classify("Guardanapo Ltda"));
        assertEquals("Outros", classifier.classify("Metropolitana Servicos"));
    }

    @Test
    void firstMatchingCategoryWinsOverTheGenericTransferKeyword() {
        assertEquals("Alimentação", classifier.classify("Pix Padaria do Zé"));
    }

    @Test
    void blankOrNullFallsBackToOthers() {
        assertEquals("Outros", classifier.classify(null));
        assertEquals("Outros", classifier.classify("   "));
    }
}
