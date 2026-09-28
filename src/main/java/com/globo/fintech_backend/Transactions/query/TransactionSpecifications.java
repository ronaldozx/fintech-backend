package com.globo.fintech_backend.Transactions.query;

import com.globo.fintech_backend.Transactions.entity.Transaction;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TransactionSpecifications {

    public static final String UNCATEGORIZED = "Outros";
    private static final char LIKE_ESCAPE = '\\';

    private TransactionSpecifications() {}

    public static Specification<Transaction> matching(TransactionFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("user").get("id"), filter.userId()));
            predicates.add(cb.between(root.get("date"), filter.startDate(), filter.endDate()));

            if (filter.type() != null) {
                predicates.add(cb.equal(root.get("type"), filter.type()));
            }
            if (filter.paymentMethod() != null) {
                predicates.add(cb.equal(root.get("paymentMethod"), filter.paymentMethod()));
            }

            String category = filter.category();
            if (category != null && !category.isBlank()) {
                if (UNCATEGORIZED.equals(category)) {
                    predicates.add(cb.or(cb.isNull(root.get("category")), cb.equal(root.get("category"), UNCATEGORIZED)));
                } else {
                    predicates.add(cb.equal(root.get("category"), category));
                }
            }

            String text = filter.query();
            if (text != null && !text.isBlank()) {
                String pattern = "%" + escapeLike(text.trim().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("description")), pattern, LIKE_ESCAPE),
                        cb.like(cb.lower(root.get("category")), pattern, LIKE_ESCAPE)
                ));
            }

            if (filter.neutral() != null) {
                if (filter.neutral()) {
                    predicates.add(cb.isTrue(root.get("neutral")));
                } else {
                    predicates.add(cb.or(cb.isNull(root.get("neutral")), cb.isFalse(root.get("neutral"))));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    static String escapeLike(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_")
                .replace("[", "\\[");
    }
}
