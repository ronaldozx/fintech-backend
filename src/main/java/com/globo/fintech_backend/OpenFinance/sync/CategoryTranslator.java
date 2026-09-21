package com.globo.fintech_backend.OpenFinance.sync;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Component
public class CategoryTranslator {

    private static final String RESOURCE = "pluggy-categories.csv";
    private static final String SEPARATOR = ";";
    private static final String CREDIT_CARD_PAYMENT = "Credit card payment";
    private static final String SAME_PERSON_TRANSFER = "Same person transfer";

    private final Map<String, String> translations;

    public CategoryTranslator() {
        this.translations = load();
    }

    public String translate(String category) {
        if (category == null || category.isBlank()) {
            return category;
        }
        String trimmed = category.trim();
        return translations.getOrDefault(trimmed, trimmed);
    }

    public boolean isNeutral(String category) {
        if (category == null) {
            return false;
        }
        String trimmed = category.trim();
        return trimmed.equals(CREDIT_CARD_PAYMENT) || trimmed.startsWith(SAME_PERSON_TRANSFER);
    }

    private static Map<String, String> load() {
        Map<String, String> loaded = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ClassPathResource(RESOURCE).getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int split = line.indexOf(SEPARATOR);
                if (split > 0) {
                    loaded.put(line.substring(0, split).trim(), line.substring(split + 1).trim());
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível carregar " + RESOURCE, e);
        }
        return Map.copyOf(loaded);
    }
}
