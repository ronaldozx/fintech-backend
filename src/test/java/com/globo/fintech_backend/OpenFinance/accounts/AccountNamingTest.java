package com.globo.fintech_backend.OpenFinance.accounts;

import com.globo.fintech_backend.OpenFinance.provider.ProviderAccount;
import com.globo.fintech_backend.OpenFinance.provider.ProviderAccountType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AccountNamingTest {

    private static ProviderAccount bank(String name, String marketingName) {
        return new ProviderAccount("b", ProviderAccountType.BANK, name, BigDecimal.ONE, "BRL", "123456",
                marketingName, null, null, null, null, null, null);
    }

    private static ProviderAccount card(String name, String brand) {
        return new ProviderAccount("c", ProviderAccountType.CREDIT, name, BigDecimal.ONE, "BRL", "9876",
                null, BigDecimal.TEN, BigDecimal.ONE, null, brand, null, null);
    }

    @Test
    void recognizesNubankByItsLegalName() {
        List<ProviderAccount> accounts = List.of(
                bank("Nu Pagamentos S.A. - Instituição de Pagamento", "Nu Pagamentos S.A. - Instituição de Pagamento (Conta Pré-paga)"));

        assertEquals("Nubank", AccountNaming.institution(accounts, "MeuPluggy"));
    }

    @Test
    void usesTheBankAccountNameAsTheInstitution() {
        assertEquals("Banco Santander", AccountNaming.institution(List.of(bank("Banco Santander", null)), "MeuPluggy"));
    }

    @Test
    void dropsParentheticalsFromUnknownInstitutions() {
        assertEquals("Banco X", AccountNaming.institution(List.of(bank("Banco X (PJ)", null)), "MeuPluggy"));
    }

    @Test
    void fallsBackToTheConnectionNameWhenThereIsNoBankAccount() {
        assertEquals("MeuPluggy", AccountNaming.institution(List.of(card("gold", "MASTERCARD")), "MeuPluggy"));
        assertEquals("Banco", AccountNaming.institution(List.of(), null));
    }

    @Test
    void namesBankAccountsAsCheckingAccounts() {
        assertEquals("Conta corrente", AccountNaming.accountName(bank("Banco Santander", null)));
    }

    @Test
    void namesCardsFromTheirNameAndBrand() {
        assertEquals("Cartão Gold · Mastercard", AccountNaming.accountName(card("gold", "MASTERCARD")));
        assertEquals("Cartão Free Visa", AccountNaming.accountName(card("FREE VISA          ", "VISA")));
        assertEquals("Cartão Visa", AccountNaming.accountName(card("", "VISA")));
        assertEquals("Cartão de crédito", AccountNaming.accountName(card("", null)));
    }

    @Test
    void masksAllButTheLastFourDigits() {
        assertEquals("•••• 3456", AccountNaming.masked("123456"));
        assertEquals("•••• 9876", AccountNaming.masked("9876"));
        assertEquals("•••• 12", AccountNaming.masked("12"));
        assertEquals("•••• 3456", AccountNaming.masked("0012345-6"));
        assertEquals("•••• 3456", AccountNaming.masked("00.123.45-6"));
        assertNull(AccountNaming.masked("abc"));
        assertNull(AccountNaming.masked(null));
        assertNull(AccountNaming.masked("  "));
    }
}
