package com.globo.fintech_backend.Insights.analysis;

import java.text.Normalizer;
import java.util.Locale;

public final class Descriptions {

    private Descriptions() {
    }

    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String withoutAccents = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return withoutAccents.toLowerCase(Locale.ROOT).replaceAll("[^a-z]+", " ").trim();
    }
}
