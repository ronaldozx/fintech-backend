package com.globo.fintech_backend.Transactions.service;

import com.globo.fintech_backend.Transactions.dto.TransactionRowDTO;
import com.globo.fintech_backend.Transactions.dto.TransactionSearchDTO;
import com.globo.fintech_backend.Transactions.entity.Transaction;
import com.globo.fintech_backend.Transactions.enums.TransactionType;
import com.globo.fintech_backend.Transactions.export.TransactionCsvWriter;
import com.globo.fintech_backend.Transactions.query.TransactionFilter;
import com.globo.fintech_backend.Transactions.query.TransactionSpecifications;
import com.globo.fintech_backend.Transactions.repository.TransactionRepository;
import com.globo.fintech_backend.exception.BadRequestException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class TransactionSearchService {

    static final int MAX_PAGE_SIZE = 200;
    static final int MAX_EXPORT_ROWS = 50_000;
    private static final Set<String> SORTABLE = Set.of("date", "amount", "description", "category");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("date"), Sort.Order.desc("id"));

    private final TransactionRepository repository;
    private final TransactionCsvWriter csvWriter;

    @PersistenceContext
    private EntityManager entityManager;

    public TransactionSearchService(TransactionRepository repository, TransactionCsvWriter csvWriter) {
        this.repository = repository;
        this.csvWriter = csvWriter;
    }

    public TransactionSearchDTO search(TransactionFilter filter, Pageable pageable) {
        requireValidRange(filter);
        Pageable safe = PageRequest.of(
                Math.max(0, pageable.getPageNumber()),
                Math.min(Math.max(1, pageable.getPageSize()), MAX_PAGE_SIZE),
                sanitize(pageable.getSort()));

        Page<TransactionRowDTO> page = repository.findAll(TransactionSpecifications.matching(filter), safe)
                .map(TransactionSearchService::toRow);
        BigDecimal[] totals = totals(filter);

        return new TransactionSearchDTO(page, totals[0], totals[1]);
    }

    public String exportCsv(TransactionFilter filter, Sort sort) {
        requireValidRange(filter);
        PageRequest request = PageRequest.of(0, MAX_EXPORT_ROWS, sanitize(sort));

        List<TransactionRowDTO> rows = repository.findAll(TransactionSpecifications.matching(filter), request)
                .map(TransactionSearchService::toRow)
                .getContent();

        return csvWriter.write(rows);
    }

    public List<String> categories(Long userId) {
        List<String> categories = new ArrayList<>(repository.findDistinctCategories(userId));
        if (repository.existsByUserIdAndCategoryIsNull(userId) && !categories.contains(TransactionSpecifications.UNCATEGORIZED)) {
            categories.add(TransactionSpecifications.UNCATEGORIZED);
            categories.sort(String.CASE_INSENSITIVE_ORDER);
        }
        return categories;
    }

    Sort sanitize(Sort requested) {
        if (requested == null || requested.isUnsorted()) {
            return DEFAULT_SORT;
        }
        List<Sort.Order> orders = new ArrayList<>();
        for (Sort.Order order : requested) {
            if (!SORTABLE.contains(order.getProperty())) {
                throw new BadRequestException("Ordenação inválida: " + order.getProperty());
            }
            orders.add(order);
        }
        orders.add(Sort.Order.desc("id"));
        return Sort.by(orders);
    }

    private BigDecimal[] totals(TransactionFilter filter) {
        BigDecimal totalIncome = sumCounted(filter, TransactionType.INCOME);
        BigDecimal totalExpense = sumCounted(filter, TransactionType.EXPENSE).abs();

        return new BigDecimal[]{totalIncome, totalExpense};
    }

    private BigDecimal sumCounted(TransactionFilter filter, TransactionType type) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<BigDecimal> query = cb.createQuery(BigDecimal.class);
        Root<Transaction> root = query.from(Transaction.class);

        Expression<BigDecimal> sum = cb.sum(root.<BigDecimal>get("amount"));
        Predicate matching = TransactionSpecifications.matching(filter).toPredicate(root, query, cb);
        Predicate counted = cb.or(cb.isNull(root.get("neutral")), cb.isFalse(root.get("neutral")));
        query.select(sum).where(matching, counted, cb.equal(root.get("type"), type));

        BigDecimal result = entityManager.createQuery(query).getSingleResult();
        return result == null ? BigDecimal.ZERO : result;
    }

    private static void requireValidRange(TransactionFilter filter) {
        if (filter.startDate().isAfter(filter.endDate())) {
            throw new BadRequestException("startDate deve ser anterior ou igual a endDate");
        }
    }

    private static TransactionRowDTO toRow(Transaction transaction) {
        return new TransactionRowDTO(
                transaction.getId(),
                transaction.getDescription(),
                transaction.getAmount(),
                transaction.getDate(),
                transaction.getType(),
                transaction.getPaymentMethod(),
                transaction.getCategory(),
                Boolean.TRUE.equals(transaction.getNeutral())
        );
    }
}
