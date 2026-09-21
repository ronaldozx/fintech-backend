package com.globo.fintech_backend.OpenFinance.sync;

import com.globo.fintech_backend.OpenFinance.connection.BankConnection;
import com.globo.fintech_backend.OpenFinance.connection.BankConnectionRepository;
import com.globo.fintech_backend.OpenFinance.provider.OpenFinanceProvider;
import com.globo.fintech_backend.OpenFinance.provider.ProviderAccount;
import com.globo.fintech_backend.OpenFinance.provider.ProviderAccountType;
import com.globo.fintech_backend.OpenFinance.provider.ProviderTransaction;
import com.globo.fintech_backend.OpenFinance.provider.ProviderTransactionType;
import com.globo.fintech_backend.Transactions.entity.Transaction;
import com.globo.fintech_backend.Transactions.enums.PaymentMethod;
import com.globo.fintech_backend.Transactions.enums.TransactionType;
import com.globo.fintech_backend.Transactions.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class TransactionSyncService {

    static final int INITIAL_HISTORY_MONTHS = 12;
    static final int OVERLAP_DAYS = 7;

    private final BankConnectionRepository connectionRepository;
    private final TransactionRepository transactionRepository;
    private final OpenFinanceProvider provider;
    private final CategoryClassifier classifier;
    private final CategoryTranslator translator;
    private final Clock clock;
    private final ConcurrentHashMap<Long, Object> userLocks = new ConcurrentHashMap<>();

    @Autowired
    public TransactionSyncService(BankConnectionRepository connectionRepository,
                                  TransactionRepository transactionRepository,
                                  OpenFinanceProvider provider,
                                  CategoryClassifier classifier,
                                  CategoryTranslator translator) {
        this(connectionRepository, transactionRepository, provider, classifier, translator, Clock.systemDefaultZone());
    }

    TransactionSyncService(BankConnectionRepository connectionRepository,
                           TransactionRepository transactionRepository,
                           OpenFinanceProvider provider,
                           CategoryClassifier classifier,
                           CategoryTranslator translator,
                           Clock clock) {
        this.connectionRepository = connectionRepository;
        this.transactionRepository = transactionRepository;
        this.provider = provider;
        this.classifier = classifier;
        this.translator = translator;
        this.clock = clock;
    }

    public SyncResultDTO syncUser(Long userId) {
        List<BankConnection> connections = connectionRepository.findByUserIdOrderByCreatedAtDesc(userId);
        int imported = 0;

        for (BankConnection connection : connections) {
            imported += syncConnection(connection);
        }
        return new SyncResultDTO(connections.size(), imported);
    }

    public int syncConnection(BankConnection connection) {
        Long userId = connection.getUser().getId();

        synchronized (userLocks.computeIfAbsent(userId, id -> new Object())) {
            LocalDate today = LocalDate.now(clock);
            LocalDate from = startOfWindow(connection, today);
            int imported = 0;

            for (ProviderAccount account : provider.listAccounts(connection.getItemId())) {
                imported += syncAccount(connection, account, from, today);
            }

            connection.setLastSyncedAt(LocalDateTime.now(clock));
            connectionRepository.save(connection);
            return imported;
        }
    }

    private LocalDate startOfWindow(BankConnection connection, LocalDate today) {
        if (connection.getLastSyncedAt() == null) {
            return today.minusMonths(INITIAL_HISTORY_MONTHS);
        }
        return connection.getLastSyncedAt().toLocalDate().minusDays(OVERLAP_DAYS);
    }

    private int syncAccount(BankConnection connection, ProviderAccount account, LocalDate from, LocalDate to) {
        List<ProviderTransaction> candidates = provider.listTransactions(account.id(), from, to).stream()
                .filter(ProviderTransaction::posted)
                .filter(transaction -> isCountable(account, transaction))
                .toList();

        if (candidates.isEmpty()) {
            return 0;
        }

        Long userId = connection.getUser().getId();
        Set<String> ids = candidates.stream().map(ProviderTransaction::id).collect(Collectors.toSet());
        Set<String> existing = transactionRepository.findExistingExternalIds(userId, ids);

        List<Transaction> created = candidates.stream()
                .filter(transaction -> !existing.contains(transaction.id()))
                .map(transaction -> toEntity(connection, account, transaction))
                .toList();

        transactionRepository.saveAll(created);
        return created.size();
    }

    private boolean isCountable(ProviderAccount account, ProviderTransaction transaction) {
        boolean cardCredit = account.type() == ProviderAccountType.CREDIT
                && transaction.type() == ProviderTransactionType.CREDIT;
        return !cardCredit;
    }

    private Transaction toEntity(BankConnection connection, ProviderAccount account, ProviderTransaction source) {
        boolean expense = source.type() == ProviderTransactionType.DEBIT;

        Transaction transaction = new Transaction();
        transaction.setUser(connection.getUser());
        transaction.setExternalId(source.id());
        transaction.setDescription(source.description() == null ? "" : source.description().trim());
        transaction.setAmount(expense ? source.amount().abs().negate() : source.amount().abs());
        transaction.setType(expense ? TransactionType.EXPENSE : TransactionType.INCOME);
        transaction.setDate(source.date());
        transaction.setPaymentMethod(account.type() == ProviderAccountType.CREDIT ? PaymentMethod.CREDIT : PaymentMethod.DEBIT);
        transaction.setCategory(resolveCategory(source));
        transaction.setNeutral(translator.isNeutral(source.category()));
        return transaction;
    }

    private String resolveCategory(ProviderTransaction source) {
        if (source.category() != null && !source.category().isBlank()) {
            return translator.translate(source.category());
        }
        return classifier.classify(source.description());
    }
}
