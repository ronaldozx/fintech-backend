package com.globo.fintech_backend.Transactions.service;

import com.globo.fintech_backend.Insights.analysis.Descriptions;
import com.globo.fintech_backend.Transactions.entity.Transaction;
import com.globo.fintech_backend.Transactions.enums.TransactionType;
import com.globo.fintech_backend.Transactions.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class OwnTransferReconciler {

    public static final String REASON = "OWN_TRANSFER";

    static final int MAX_DAYS_APART = 1;

    private final TransactionRepository transactionRepository;

    public OwnTransferReconciler(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public int reconcile(Long userId) {
        List<Transaction[]> pairs = match(transactionRepository.findTransferCandidates(userId));

        List<Transaction> changed = new ArrayList<>();
        for (Transaction[] pair : pairs) {
            for (Transaction side : pair) {
                side.setNeutral(true);
                side.setNeutralReason(REASON);
                changed.add(side);
            }
        }

        transactionRepository.saveAll(changed);
        return pairs.size();
    }

    static List<Transaction[]> match(List<Transaction> candidates) {
        List<Transaction> transfers = candidates.stream().filter(OwnTransferReconciler::isOwnTransferCategory).toList();
        List<Transaction> incomes = transfers.stream().filter(t -> t.getType() == TransactionType.INCOME).toList();

        Set<Transaction> used = new HashSet<>();
        List<Transaction[]> pairs = new ArrayList<>();

        for (Transaction expense : transfers) {
            if (expense.getType() != TransactionType.EXPENSE) {
                continue;
            }

            incomes.stream()
                    .filter(income -> !used.contains(income))
                    .filter(income -> income.getAmount().compareTo(expense.getAmount().abs()) == 0)
                    .filter(income -> daysApart(expense, income) <= MAX_DAYS_APART)
                    .min(Comparator.<Transaction>comparingLong(income -> daysApart(expense, income))
                            .thenComparing(Transaction::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                    .ifPresent(income -> {
                        used.add(income);
                        pairs.add(new Transaction[]{expense, income});
                    });
        }

        return pairs;
    }

    static boolean isOwnTransferCategory(Transaction transaction) {
        if (transaction.getCategory() == null) {
            return false;
        }
        String normalized = Descriptions.normalize(transaction.getCategory());
        return normalized.startsWith("transferencia")
                && !normalized.contains("terceiros")
                && !normalized.contains("titularidade");
    }

    private static long daysApart(Transaction a, Transaction b) {
        return Math.abs(ChronoUnit.DAYS.between(a.getDate(), b.getDate()));
    }
}
