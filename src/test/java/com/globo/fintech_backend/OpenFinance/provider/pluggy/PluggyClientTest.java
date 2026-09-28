package com.globo.fintech_backend.OpenFinance.provider.pluggy;

import com.globo.fintech_backend.OpenFinance.exception.OpenFinanceException;
import com.globo.fintech_backend.OpenFinance.exception.OpenFinanceUnavailableException;
import com.globo.fintech_backend.OpenFinance.provider.ProviderAccount;
import com.globo.fintech_backend.OpenFinance.provider.ProviderAccountType;
import com.globo.fintech_backend.OpenFinance.provider.ProviderItem;
import com.globo.fintech_backend.OpenFinance.provider.ProviderTransaction;
import com.globo.fintech_backend.OpenFinance.provider.ProviderTransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;

class PluggyClientTest {

    private static final String BASE_URL = "https://api.pluggy.ai";
    private static final String FIRST_TRANSACTIONS_URL =
            BASE_URL + "/v2/transactions?accountId=acc-1&dateFrom=2026-01-01&dateTo=2026-01-31";

    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    private MockRestServiceServer server;
    private PluggyClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new PluggyClient(builder, new PluggyProperties("client-id", "client-secret", BASE_URL), clock);
    }

    private void expectAuth(String apiKey) {
        server.expect(once(), requestTo(BASE_URL + "/auth"))
                .andExpect(method(POST))
                .andExpect(jsonPath("$.clientId").value("client-id"))
                .andExpect(jsonPath("$.clientSecret").value("client-secret"))
                .andRespond(withSuccess("{\"apiKey\":\"" + apiKey + "\"}", MediaType.APPLICATION_JSON));
    }

    @Test
    void createsConnectTokenForTheUser() {
        expectAuth("key-1");
        server.expect(once(), requestTo(BASE_URL + "/connect_token"))
                .andExpect(method(POST))
                .andExpect(header("X-API-KEY", "key-1"))
                .andExpect(jsonPath("$.options.clientUserId").value("42"))
                .andRespond(withSuccess("{\"accessToken\":\"connect-abc\"}", MediaType.APPLICATION_JSON));

        assertEquals("connect-abc", client.createConnectToken(42L));
        server.verify();
    }

    @Test
    void reusesTheApiKeyWhileItIsValid() {
        expectAuth("key-1");
        server.expect(once(), requestTo(BASE_URL + "/connect_token"))
                .andExpect(header("X-API-KEY", "key-1"))
                .andRespond(withSuccess("{\"accessToken\":\"t1\"}", MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(BASE_URL + "/connect_token"))
                .andExpect(header("X-API-KEY", "key-1"))
                .andRespond(withSuccess("{\"accessToken\":\"t2\"}", MediaType.APPLICATION_JSON));

        client.createConnectToken(1L);
        clock.advance(Duration.ofMinutes(60));
        client.createConnectToken(1L);

        server.verify();
    }

    @Test
    void authenticatesAgainAfterTheApiKeyExpires() {
        expectAuth("key-1");
        server.expect(once(), requestTo(BASE_URL + "/connect_token"))
                .andExpect(header("X-API-KEY", "key-1"))
                .andRespond(withSuccess("{\"accessToken\":\"t1\"}", MediaType.APPLICATION_JSON));
        expectAuth("key-2");
        server.expect(once(), requestTo(BASE_URL + "/connect_token"))
                .andExpect(header("X-API-KEY", "key-2"))
                .andRespond(withSuccess("{\"accessToken\":\"t2\"}", MediaType.APPLICATION_JSON));

        client.createConnectToken(1L);
        clock.advance(Duration.ofMinutes(115));
        client.createConnectToken(1L);

        server.verify();
    }

    @Test
    void listsAccountsAcrossPages() {
        expectAuth("key-1");
        server.expect(once(), requestTo(BASE_URL + "/accounts?itemId=item-1&page=1"))
                .andExpect(method(GET))
                .andExpect(header("X-API-KEY", "key-1"))
                .andRespond(withSuccess("""
                        {"page":1,"total":2,"totalPages":2,"results":[
                          {"id":"acc-1","type":"BANK","subtype":"CHECKING_ACCOUNT","name":"Conta Corrente","balance":1500.25,"currencyCode":"BRL","itemId":"item-1"}
                        ]}
                        """, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(BASE_URL + "/accounts?itemId=item-1&page=2"))
                .andRespond(withSuccess("""
                        {"page":2,"total":2,"totalPages":2,"results":[
                          {"id":"acc-2","type":"CREDIT","name":"Cartão","balance":300.00,"currencyCode":"BRL"}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        List<ProviderAccount> accounts = client.listAccounts("item-1");

        assertEquals(2, accounts.size());
        assertEquals("acc-1", accounts.get(0).id());
        assertEquals(ProviderAccountType.BANK, accounts.get(0).type());
        assertEquals(new BigDecimal("1500.25"), accounts.get(0).balance());
        assertEquals(ProviderAccountType.CREDIT, accounts.get(1).type());
        server.verify();
    }

    @Test
    void mapsTransactionsIncludingDateStatusAndOptionalCategory() {
        expectAuth("key-1");
        server.expect(once(), requestTo(FIRST_TRANSACTIONS_URL))
                .andExpect(header("X-API-KEY", "key-1"))
                .andRespond(withSuccess("""
                        {"results":[
                          {"id":"t-1","description":"Mercado","amount":-150.50,"date":"2026-01-05T00:00:00.000Z",
                           "type":"DEBIT","status":"POSTED","category":"Groceries","currencyCode":"BRL","balance":100,
                           "operationType":"PIX","merchant":null,"paymentData":null},
                          {"id":"t-2","description":"Salário","amount":3000,"date":"2026-01-10",
                           "type":"CREDIT","status":"PENDING","currencyCode":"BRL"}
                        ],"next":null}
                        """, MediaType.APPLICATION_JSON));

        List<ProviderTransaction> transactions =
                client.listTransactions("acc-1", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        assertEquals(2, transactions.size());

        ProviderTransaction first = transactions.get(0);
        assertEquals("t-1", first.id());
        assertEquals(LocalDate.of(2026, 1, 5), first.date());
        assertEquals(ProviderTransactionType.DEBIT, first.type());
        assertEquals(new BigDecimal("-150.50"), first.amount());
        assertEquals("Groceries", first.category());
        assertTrue(first.posted());

        ProviderTransaction second = transactions.get(1);
        assertEquals(LocalDate.of(2026, 1, 10), second.date());
        assertEquals(ProviderTransactionType.CREDIT, second.type());
        assertEquals(null, second.category());
        assertFalse(second.posted());
        server.verify();
    }

    private static String transactionsPage(String id, String next) {
        String cursor = next == null ? "null" : "\"" + next + "\"";
        return "{\"results\":[{\"id\":\"" + id + "\",\"description\":\"x\",\"amount\":-1,"
                + "\"date\":\"2026-01-05T00:00:00.000Z\",\"type\":\"DEBIT\",\"status\":\"POSTED\"}],\"next\":" + cursor + "}";
    }

    private List<ProviderTransaction> fetchJanuary() {
        return client.listTransactions("acc-1", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
    }

    @Test
    void followsAQueryStringCursorUntilItIsNull() {
        expectAuth("key-1");
        server.expect(once(), requestTo(FIRST_TRANSACTIONS_URL))
                .andRespond(withSuccess(transactionsPage("t-1", "accountId=acc-1&after=abc"), MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(BASE_URL + "/v2/transactions?accountId=acc-1&after=abc"))
                .andRespond(withSuccess(transactionsPage("t-2", null), MediaType.APPLICATION_JSON));

        List<ProviderTransaction> transactions = fetchJanuary();

        assertEquals(List.of("t-1", "t-2"), transactions.stream().map(ProviderTransaction::id).toList());
        server.verify();
    }

    @Test
    void followsACursorThatComesWithALeadingQuestionMark() {
        expectAuth("key-1");
        server.expect(once(), requestTo(FIRST_TRANSACTIONS_URL))
                .andRespond(withSuccess(transactionsPage("t-1", "?accountId=acc-1&after=abc"), MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(BASE_URL + "/v2/transactions?accountId=acc-1&after=abc"))
                .andRespond(withSuccess(transactionsPage("t-2", null), MediaType.APPLICATION_JSON));

        assertEquals(2, fetchJanuary().size());
        server.verify();
    }

    @Test
    void followsAFullUrlCursorOnTheSameHost() {
        expectAuth("key-1");
        server.expect(once(), requestTo(FIRST_TRANSACTIONS_URL))
                .andRespond(withSuccess(transactionsPage("t-1", BASE_URL + "/v2/transactions?accountId=acc-1&after=abc"), MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(BASE_URL + "/v2/transactions?accountId=acc-1&after=abc"))
                .andRespond(withSuccess(transactionsPage("t-2", null), MediaType.APPLICATION_JSON));

        assertEquals(2, fetchJanuary().size());
        server.verify();
    }

    @Test
    void sendsABareCursorAsTheAfterParameter() {
        expectAuth("key-1");
        server.expect(once(), requestTo(FIRST_TRANSACTIONS_URL))
                .andRespond(withSuccess(transactionsPage("t-1", "opaqueToken"), MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(FIRST_TRANSACTIONS_URL + "&after=opaqueToken"))
                .andRespond(withSuccess(transactionsPage("t-2", null), MediaType.APPLICATION_JSON));

        assertEquals(2, fetchJanuary().size());
        server.verify();
    }

    @Test
    void refusesACursorThatPointsToAnotherServer() {
        expectAuth("key-1");
        server.expect(once(), requestTo(FIRST_TRANSACTIONS_URL))
                .andRespond(withSuccess(transactionsPage("t-1", "https://evil.example.com/steal?x=1"), MediaType.APPLICATION_JSON));

        assertThrows(OpenFinanceException.class, this::fetchJanuary);
        server.verify();
    }

    @Test
    void stopsWhenTheProviderRepeatsTheSameCursor() {
        expectAuth("key-1");
        server.expect(once(), requestTo(FIRST_TRANSACTIONS_URL))
                .andRespond(withSuccess(transactionsPage("t-1", "accountId=acc-1&after=abc"), MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(BASE_URL + "/v2/transactions?accountId=acc-1&after=abc"))
                .andRespond(withSuccess(transactionsPage("t-2", "accountId=acc-1&after=abc"), MediaType.APPLICATION_JSON));

        assertThrows(OpenFinanceException.class, this::fetchJanuary);
        server.verify();
    }

    @Test
    void getsItemWithInstitutionNameAndClientUserId() {
        expectAuth("key-1");
        server.expect(once(), requestTo(BASE_URL + "/items/item-1"))
                .andExpect(method(GET))
                .andExpect(header("X-API-KEY", "key-1"))
                .andRespond(withSuccess("""
                        {"id":"item-1","status":"UPDATED","executionStatus":"SUCCESS","clientUserId":"42",
                         "connector":{"id":201,"name":"Banco Teste","imageUrl":"https://example.com/x.png"}}
                        """, MediaType.APPLICATION_JSON));

        ProviderItem item = client.getItem("item-1");

        assertEquals("item-1", item.id());
        assertEquals("UPDATED", item.status());
        assertEquals("Banco Teste", item.institutionName());
        assertEquals("42", item.clientUserId());
        server.verify();
    }

    @Test
    void deletesItem() {
        expectAuth("key-1");
        server.expect(once(), requestTo(BASE_URL + "/items/item-1"))
                .andExpect(method(DELETE))
                .andExpect(header("X-API-KEY", "key-1"))
                .andRespond(withSuccess("{\"count\":1}", MediaType.APPLICATION_JSON));

        client.deleteItem("item-1");

        server.verify();
    }

    @Test
    void failsWithUnavailableWhenCredentialsAreMissing() {
        PluggyClient unconfigured = new PluggyClient(
                RestClient.builder(), new PluggyProperties("", null, BASE_URL), clock);

        assertThrows(OpenFinanceUnavailableException.class, () -> unconfigured.createConnectToken(1L));
    }

    @Test
    void wrapsProviderFailuresInOpenFinanceException() {
        server.expect(once(), requestTo(BASE_URL + "/auth")).andRespond(withServerError());

        assertThrows(OpenFinanceException.class, () -> client.createConnectToken(1L));
    }

    @Test
    void mapsInvestmentsUsingTheOriginalAmountAndTheIssueDateAsFallback() {
        expectAuth("key-1");
        server.expect(once(), requestTo(BASE_URL + "/investments?itemId=item-1&page=1"))
                .andExpect(method(GET))
                .andExpect(header("X-API-KEY", "key-1"))
                .andRespond(withSuccess("""
                        {"total":2,"totalPages":1,"page":1,"results":[
                          {"id":"i-1","name":"CDB Banco X","type":"FIXED_INCOME","subtype":"CDB","balance":1050.5,
                           "amountOriginal":1000,"amountProfit":50.5,"dueDate":"2028-05-10T00:00:00.000Z",
                           "purchaseDate":"2026-01-10T00:00:00.000Z","issuer":"Banco X","rate":100,"rateType":"CDI","status":"ACTIVE"},
                          {"id":"i-2","name":"Fundo Y","type":"MUTUAL_FUND","balance":null,"amountOriginal":null,
                           "issueDate":"2025-12-01T00:00:00.000Z"}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        List<com.globo.fintech_backend.OpenFinance.provider.ProviderInvestment> investments = client.listInvestments("item-1");

        assertEquals(2, investments.size());
        assertEquals("CDB", investments.get(0).subtype());
        assertEquals(new BigDecimal("1050.5"), investments.get(0).balance());
        assertEquals(new BigDecimal("1000"), investments.get(0).invested());
        assertEquals(LocalDate.of(2028, 5, 10), investments.get(0).dueDate());
        assertEquals(LocalDate.of(2026, 1, 10), investments.get(0).purchaseDate());
        assertEquals("CDI", investments.get(0).rateType());
        assertEquals(BigDecimal.ZERO, investments.get(1).balance());
        assertEquals(LocalDate.of(2025, 12, 1), investments.get(1).purchaseDate());
        server.verify();
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
