package com.globo.fintech_backend.OpenFinance.sync;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class CategoryClassifier {

    public static final String FALLBACK_CATEGORY = "Outros";

    private static final Map<String, List<String>> KEYWORDS = new LinkedHashMap<>();

    static {
        KEYWORDS.put("Alimentação", List.of("mercado", "supermercado", "padaria", "restaurante", "lanchonete",
                "ifood", "rappi", "acougue", "hortifruti", "pizzaria", "burger", "cafe"));
        KEYWORDS.put("Transporte", List.of("uber", "99app", "99 pop", "posto", "combustivel", "estacionamento",
                "pedagio", "sem parar", "metro", "onibus", "bilhete unico"));
        KEYWORDS.put("Moradia", List.of("aluguel", "condominio", "energia", "enel", "cpfl", "sabesp", "agua",
                "gas", "iptu", "internet", "vivo fibra", "claro"));
        KEYWORDS.put("Saúde", List.of("farmacia", "drogaria", "droga raia", "drogasil", "hospital", "clinica",
                "laboratorio", "unimed", "plano de saude", "consulta"));
        KEYWORDS.put("Educação", List.of("escola", "faculdade", "curso", "udemy", "alura", "mensalidade escolar",
                "livraria"));
        KEYWORDS.put("Lazer", List.of("netflix", "spotify", "cinema", "ingresso", "steam", "playstation",
                "disney", "prime video", "hbo", "viagem", "hotel", "airbnb"));
        KEYWORDS.put("Compras", List.of("amazon", "mercado livre", "magazine", "americanas", "shopee",
                "aliexpress", "renner", "zara", "shein", "casas bahia"));
        KEYWORDS.put("Renda", List.of("salario", "folha de pagamento", "pagamento de salario", "rendimento",
                "dividendo", "proventos"));
        KEYWORDS.put("Transferências", List.of("pix", "ted", "doc", "transferencia"));
    }

    public String classify(String description) {
        if (description == null || description.isBlank()) {
            return FALLBACK_CATEGORY;
        }

        String normalized = normalize(description);

        for (Map.Entry<String, List<String>> entry : KEYWORDS.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (containsWord(normalized, keyword)) {
                    return entry.getKey();
                }
            }
        }
        return FALLBACK_CATEGORY;
    }

    private static boolean containsWord(String text, String keyword) {
        int from = 0;
        while (true) {
            int index = text.indexOf(keyword, from);
            if (index < 0) {
                return false;
            }
            boolean startsWord = index == 0 || !Character.isLetterOrDigit(text.charAt(index - 1));
            int end = index + keyword.length();
            boolean endsWord = end == text.length() || !Character.isLetterOrDigit(text.charAt(end));
            if (startsWord && endsWord) {
                return true;
            }
            from = index + 1;
        }
    }

    private static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
}
