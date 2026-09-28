package com.globo.fintech_backend.Transactions.query;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TransactionSpecificationsTest {

    @Test
    void escapesLikeWildcardsSoUserInputIsTakenLiterally() {
        assertEquals("100\\%", TransactionSpecifications.escapeLike("100%"));
        assertEquals("a\\_b", TransactionSpecifications.escapeLike("a_b"));
        assertEquals("\\[x]", TransactionSpecifications.escapeLike("[x]"));
    }

    @Test
    void escapesTheEscapeCharacterItself() {
        assertEquals("a\\\\b", TransactionSpecifications.escapeLike("a\\b"));
    }

    @Test
    void leavesPlainTextUntouched() {
        assertEquals("mercado livre", TransactionSpecifications.escapeLike("mercado livre"));
    }
}
