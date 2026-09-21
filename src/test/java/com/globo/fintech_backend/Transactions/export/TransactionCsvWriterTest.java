package com.globo.fintech_backend.Transactions.export;

import com.globo.fintech_backend.Transactions.dto.TransactionRowDTO;
import com.globo.fintech_backend.Transactions.enums.PaymentMethod;
import com.globo.fintech_backend.Transactions.enums.TransactionType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionCsvWriterTest {

    private final TransactionCsvWriter writer = new TransactionCsvWriter();

    private static TransactionRowDTO row(String description, String amount, TransactionType type,
                                         PaymentMethod method, String category, boolean neutral) {
        return new TransactionRowDTO(1L, description, new BigDecimal(amount), LocalDate.of(2026, 9, 4),
                type, method, category, neutral);
    }

    @Test
    void startsWithABomAndTheHeader() {
        String csv = writer.write(List.of());

        assertTrue(csv.startsWith("﻿Data;Descrição;Categoria;Tipo;Forma de pagamento;Valor;Conta nos totais"));
    }

    @Test
    void writesOneRowPerTransactionWithBrazilianFormats() {
        String csv = writer.write(List.of(
                row("Mercado", "-150.50", TransactionType.EXPENSE, PaymentMethod.DEBIT, "Supermercado", false),
                row("Salário", "3000.00", TransactionType.INCOME, PaymentMethod.DEBIT, "Salário", false)
        ));

        String[] lines = csv.split("\r\n");
        assertEquals("04/09/2026;Mercado;Supermercado;Despesa;Débito;-150,50;Sim", lines[1]);
        assertEquals("04/09/2026;Salário;Salário;Receita;Débito;3000,00;Sim", lines[2]);
    }

    @Test
    void marksNeutralRowsAsNotCountingTowardsTotals() {
        String csv = writer.write(List.of(
                row("Fatura", "-1500.00", TransactionType.EXPENSE, PaymentMethod.DEBIT, "Pagamento de cartão de crédito", true)
        ));

        assertTrue(csv.split("\r\n")[1].endsWith(";Não"));
    }

    @Test
    void usesOthersForAMissingCategoryAndCreditForCardPurchases() {
        String csv = writer.write(List.of(
                row("Loja", "-10.00", TransactionType.EXPENSE, PaymentMethod.CREDIT, null, false)
        ));

        assertEquals("04/09/2026;Loja;Outros;Despesa;Crédito;-10,00;Sim", csv.split("\r\n")[1]);
    }

    @Test
    void quotesFieldsThatContainTheSeparatorOrQuotes() {
        assertEquals("\"a;b\"", TransactionCsvWriter.escape("a;b"));
        assertEquals("\"diz \"\"oi\"\"\"", TransactionCsvWriter.escape("diz \"oi\""));
    }

    @Test
    void flattensLineBreaksInsideAField() {
        assertEquals("linha um linha dois", TransactionCsvWriter.escape("linha um\nlinha dois"));
    }

    @Test
    void neutralizesSpreadsheetFormulas() {
        assertEquals("'=SOMA(A1)", TransactionCsvWriter.escape("=SOMA(A1)"));
        assertEquals("'+55 11", TransactionCsvWriter.escape("+55 11"));
        assertEquals("'@cmd", TransactionCsvWriter.escape("@cmd"));
        assertEquals("'-2+3", TransactionCsvWriter.escape("-2+3"));
    }

    @Test
    void nullBecomesAnEmptyField() {
        assertEquals("", TransactionCsvWriter.escape(null));
    }
}
