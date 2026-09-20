package com.globo.fintech_backend.OpenFinance.provider.pluggy;

import com.globo.fintech_backend.OpenFinance.exception.OpenFinanceException;
import com.globo.fintech_backend.OpenFinance.exception.OpenFinanceUnavailableException;
import com.globo.fintech_backend.OpenFinance.provider.OpenFinanceProvider;
import com.globo.fintech_backend.OpenFinance.provider.ProviderAccount;
import com.globo.fintech_backend.OpenFinance.provider.ProviderAccountType;
import com.globo.fintech_backend.OpenFinance.provider.ProviderItem;
import com.globo.fintech_backend.OpenFinance.provider.ProviderTransaction;
import com.globo.fintech_backend.OpenFinance.provider.ProviderTransactionType;
import com.globo.fintech_backend.OpenFinance.provider.pluggy.PluggyResponses.Account;
import com.globo.fintech_backend.OpenFinance.provider.pluggy.PluggyResponses.AuthRequest;
import com.globo.fintech_backend.OpenFinance.provider.pluggy.PluggyResponses.AuthResponse;
import com.globo.fintech_backend.OpenFinance.provider.pluggy.PluggyResponses.ConnectTokenOptions;
import com.globo.fintech_backend.OpenFinance.provider.pluggy.PluggyResponses.ConnectTokenRequest;
import com.globo.fintech_backend.OpenFinance.provider.pluggy.PluggyResponses.ConnectTokenResponse;
import com.globo.fintech_backend.OpenFinance.provider.pluggy.PluggyResponses.Item;
import com.globo.fintech_backend.OpenFinance.provider.pluggy.PluggyResponses.Page;
import com.globo.fintech_backend.OpenFinance.provider.pluggy.PluggyResponses.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.Supplier;

@Component
public class PluggyClient implements OpenFinanceProvider {

    private static final String API_KEY_HEADER = "X-API-KEY";
    private static final Duration API_KEY_TTL = Duration.ofMinutes(110);
    private static final int PAGE_SIZE = 500;
    private static final int FIRST_PAGE = 1;

    private final RestClient restClient;
    private final PluggyProperties properties;
    private final Clock clock;

    private String apiKey;
    private Instant apiKeyExpiresAt = Instant.MIN;

    @Autowired
    public PluggyClient(PluggyProperties properties) {
        this(RestClient.builder(), properties, Clock.systemUTC());
    }

    PluggyClient(RestClient.Builder builder, PluggyProperties properties, Clock clock) {
        this.restClient = builder.baseUrl(properties.baseUrl()).build();
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public String createConnectToken(Long userId) {
        ConnectTokenRequest body = new ConnectTokenRequest(new ConnectTokenOptions(String.valueOf(userId)));

        ConnectTokenResponse response = call("create connect token", () -> restClient.post()
                .uri("/connect_token")
                .header(API_KEY_HEADER, getApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(ConnectTokenResponse.class));

        if (response == null || response.accessToken() == null) {
            throw new OpenFinanceException("Resposta inválida do provedor ao criar o token de conexão");
        }
        return response.accessToken();
    }

    @Override
    public ProviderItem getItem(String itemId) {
        Item item = call("get item", () -> restClient.get()
                .uri("/items/{itemId}", itemId)
                .header(API_KEY_HEADER, getApiKey())
                .retrieve()
                .body(Item.class));

        if (item == null || item.id() == null) {
            throw new OpenFinanceException("Resposta inválida do provedor ao consultar a conexão");
        }
        String institutionName = item.connector() == null ? null : item.connector().name();
        return new ProviderItem(item.id(), item.status(), institutionName, item.clientUserId());
    }

    @Override
    public void deleteItem(String itemId) {
        call("delete item", () -> restClient.delete()
                .uri("/items/{itemId}", itemId)
                .header(API_KEY_HEADER, getApiKey())
                .retrieve()
                .toBodilessEntity());
    }

    @Override
    public List<ProviderAccount> listAccounts(String itemId) {
        List<Account> accounts = fetchAll("list accounts", page -> restClient.get()
                .uri("/accounts?itemId={itemId}&page={page}", itemId, page)
                .header(API_KEY_HEADER, getApiKey())
                .retrieve()
                .body(new ParameterizedTypeReference<Page<Account>>() {}));

        return accounts.stream().map(this::toProviderAccount).toList();
    }

    @Override
    public List<ProviderTransaction> listTransactions(String accountId, LocalDate from, LocalDate to) {
        List<Transaction> transactions = fetchAll("list transactions", page -> restClient.get()
                .uri("/transactions?accountId={accountId}&from={from}&to={to}&pageSize={pageSize}&page={page}",
                        accountId, from, to, PAGE_SIZE, page)
                .header(API_KEY_HEADER, getApiKey())
                .retrieve()
                .body(new ParameterizedTypeReference<Page<Transaction>>() {}));

        return transactions.stream().map(this::toProviderTransaction).toList();
    }

    private <T> List<T> fetchAll(String operation, IntFunction<Page<T>> fetchPage) {
        List<T> all = new ArrayList<>();
        int page = FIRST_PAGE;
        int totalPages;

        do {
            int current = page;
            Page<T> result = call(operation, () -> fetchPage.apply(current));
            if (result == null || result.results() == null) {
                throw new OpenFinanceException("Resposta inválida do provedor ao executar: " + operation);
            }
            all.addAll(result.results());
            totalPages = result.totalPages();
            page++;
        } while (page <= totalPages);

        return all;
    }

    private <T> T call(String operation, Supplier<T> request) {
        if (!properties.isConfigured()) {
            throw new OpenFinanceUnavailableException("Open Finance não configurado no servidor");
        }
        try {
            return request.get();
        } catch (RestClientException e) {
            throw new OpenFinanceException("Falha ao comunicar com o provedor de Open Finance (" + operation + ")", e);
        }
    }

    private synchronized String getApiKey() {
        Instant now = clock.instant();
        if (apiKey == null || !now.isBefore(apiKeyExpiresAt)) {
            AuthResponse response = call("authenticate", () -> restClient.post()
                    .uri("/auth")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new AuthRequest(properties.clientId(), properties.clientSecret()))
                    .retrieve()
                    .body(AuthResponse.class));

            if (response == null || response.apiKey() == null) {
                throw new OpenFinanceException("Resposta inválida do provedor ao autenticar");
            }
            apiKey = response.apiKey();
            apiKeyExpiresAt = now.plus(API_KEY_TTL);
        }
        return apiKey;
    }

    private ProviderAccount toProviderAccount(Account account) {
        return new ProviderAccount(
                account.id(),
                ProviderAccountType.valueOf(account.type()),
                account.name(),
                account.balance(),
                account.currencyCode()
        );
    }

    private ProviderTransaction toProviderTransaction(Transaction transaction) {
        return new ProviderTransaction(
                transaction.id(),
                transaction.description(),
                transaction.amount(),
                parseDate(transaction.date()),
                ProviderTransactionType.valueOf(transaction.type()),
                !"PENDING".equals(transaction.status()),
                transaction.category()
        );
    }

    private LocalDate parseDate(String date) {
        if (date.length() == 10) {
            return LocalDate.parse(date);
        }
        return OffsetDateTime.parse(date).toLocalDate();
    }
}
