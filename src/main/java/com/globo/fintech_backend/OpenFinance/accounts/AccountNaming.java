package com.globo.fintech_backend.OpenFinance.accounts;

import com.globo.fintech_backend.OpenFinance.provider.ProviderAccount;
import com.globo.fintech_backend.OpenFinance.provider.ProviderAccountType;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

final class AccountNaming {

    private static final String DEFAULT_INSTITUTION = "Banco";

    private AccountNaming() {}

    static String institution(List<ProviderAccount> accounts, String fallback) {
        return accounts.stream()
                .filter(account -> account.type() == ProviderAccountType.BANK)
                .map(AccountNaming::rawInstitution)
                .filter(name -> name != null && !name.isBlank())
                .findFirst()
                .map(AccountNaming::friendlyInstitution)
                .orElseGet(() -> fallback == null || fallback.isBlank() ? DEFAULT_INSTITUTION : fallback);
    }

    static String accountName(ProviderAccount account) {
        if (account.type() == ProviderAccountType.CREDIT) {
            return cardName(account);
        }
        return "Conta corrente";
    }

    static String masked(String number) {
        if (number == null || number.isBlank()) {
            return null;
        }
        String digits = number.replaceAll("\\D", "");
        if (digits.isEmpty()) {
            return null;
        }
        String tail = digits.length() <= 4 ? digits : digits.substring(digits.length() - 4);
        return "•••• " + tail;
    }

    private static String rawInstitution(ProviderAccount account) {
        String marketing = account.marketingName();
        return marketing != null && !marketing.isBlank() ? marketing : account.name();
    }

    private static String friendlyInstitution(String raw) {
        String plain = Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
        if (plain.contains("nu pagamentos") || plain.contains("nubank")) {
            return "Nubank";
        }
        return raw.replaceAll("\\s*\\(.*?\\)\\s*", " ").trim();
    }

    private static String cardName(ProviderAccount account) {
        String name = account.name() == null ? "" : account.name().trim();
        String brand = account.brand() == null ? "" : titleCase(account.brand());
        String title = titleCase(name);

        if (title.isBlank()) {
            return brand.isBlank() ? "Cartão de crédito" : "Cartão " + brand;
        }
        boolean nameAlreadyHasBrand = !brand.isBlank() && title.toLowerCase(Locale.ROOT).contains(brand.toLowerCase(Locale.ROOT));
        return nameAlreadyHasBrand || brand.isBlank() ? "Cartão " + title : "Cartão " + title + " · " + brand;
    }

    private static String titleCase(String text) {
        return Arrays.stream(text.trim().split("\\s+"))
                .filter(word -> !word.isBlank())
                .map(word -> word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1).toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(" "));
    }
}
