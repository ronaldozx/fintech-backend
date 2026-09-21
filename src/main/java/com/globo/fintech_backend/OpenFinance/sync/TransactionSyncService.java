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
    static final int MAX_ERROR_LENGTH = 300;
    private static final String CARD_PAYMENT_CATEGORY = "Credit card payment";

    private final BankConnectionRepository connectionRepository;
    private final TransactionRepository transactionRepository;
    private final OpenFinanceProvider provider;
    private final CategoryClassifier classifier;
    private final CategoryTranslator translator;
    private final CardBillPaymentDetector billDetector;
    private final Clock clock;
    private final ConcurrentHashMap<Long, Object> userLocks = new ConcurrentHashMap<>();

    @Autowired
    public TransactionSyncService(BankConnectionRepository connectionRepository,
                                  TransactionRepository transactionRepository,
                                  OpenFinanceProvider provider,
                                  CategoryClassifier classifier,
                                  CategoryTranslator translator,
                                  CardBillPaymentDetector billDetector) {
        this(connectionRepository, transactionRepository, provider, classifier, translator, billDetector,
                Clock.systemDefaultZone());
    }

    TransactionSyncService(BankConnectionRepository connectionRepository,
                           TransactionRepository transactionRepository,
                           OpenFinanceProvider provider,
                           CategoryClassifier classifier,
                           CategoryTranslator translator,
                           CardBillPaymentDetector billDetector,
                           Clock clock) {
        this.connectionRepository = connectionRepository;
        this.transactionRepository = transactionRepository;
        this.provider = provider;
        this.classifier = classifier;
        this.translator = translator;
        this.billDetector = billDetector;
        this.clock = clock;
    }

    public SyncResultDTO syncUser(Long userId) {
        List<BankConnection> connections = connectionRepository.findByUserIdOrderByCreatedAtDesc(userId);
        int imported = 0;
        int failed = 0;
        RuntimeException firstFailure = null;

        for (BankConnection connection : connections) {
            try {
                imported += syncConnection(connection);
            } catch (RuntimeException e) {
                failed++;
                if (firstFailure == null) {
                    firstFailure = e;
                }
            }
        }

        if (firstFailure != null && failed == connections.size()) {
            throw firstFailure;
        }
        return new SyncResultDTO(connections.size(), imported, 0, failed);
    }

    public int syncConnection(BankConnection connection) {
        Long userId = connection.getUser().getId();

        synchronized (userLocks.computeIfAbsent(userId, id -> new Object())) {
            LocalDate today = LocalDate.now(clock);
            LocalDateTime attempt = LocalDateTime.now(clock);
            LocalDate from = startOfWindow(connection, today);
            int imported = 0;

            try {
                for (ProviderAccount account : provider.listAccounts(connection.getItemId())) {
                    imported += syncAccount(connection, account, from, today);
                }
            } catch (RuntimeException e) {
                connection.setLastSyncAttemptAt(attempt);
                connection.setLastSyncError(describe(e));
                connectionRepository.save(connection);
                throw e;
            }

            connection.setLastSyncedAt(attempt);
            connection.setLastSyncAttemptAt(attempt);
            connection.setLastSyncError(null);
            connectionRepository.save(connection);
            return imported;
        }
    }

    private static String describe(RuntimeException error) {
        String message = error.getMessage() == null || error.getMessage().isBlank()
                ? error.getClass().getSimpleName()
                : error.getMessage().trim();
        return message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) : message;
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

        boolean bankBillPayment = expense
                && account.type() == ProviderAccountType.BANK
                && billDetector.matches(source.description());
        if (bankBillPayment) {
            transaction.setNeutral(true);
            transaction.setCategory(translator.translate(CARD_PAYMENT_CATEGORY));
        }
        return transaction;
    }

    private String resolveCategory(ProviderTransaction source) {
        if (source.category() != null && !source.category().isBlank()) {
            return translator.translate(source.category());
        }
        return classifier.classify(source.description());
    }
}
