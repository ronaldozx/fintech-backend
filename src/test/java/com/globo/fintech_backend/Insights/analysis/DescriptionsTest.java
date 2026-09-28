package com.globo.fintech_backend.Insights.analysis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DescriptionsTest {

    @Test
    void dropsAccentsCaseDigitsAndPunctuation() {
        assertEquals("netflix com", Descriptions.normalize("NETFLIX.COM 123456"));
        assertEquals("padaria do ze", Descriptions.normalize("  Padaria do Zé!! "));
        assertEquals("cafe sao joao", Descriptions.normalize("Café São João"));
    }

    @Test
    void textWithoutLettersBecomesEmpty() {
        assertEquals("", Descriptions.normalize("12345 - 99"));
        assertEquals("", Descriptions.normalize(null));
        assertEquals("", Descriptions.normalize("   "));
    }
}
