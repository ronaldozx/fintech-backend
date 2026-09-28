package com.globo.fintech_backend.Transactions.repository;

import com.globo.fintech_backend.Transactions.dto.TransactionDTO;
import com.globo.fintech_backend.Transactions.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.globo.fintech_backend.Transactions.enums.PaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {
    Page<TransactionDTO> findByUserIdAndDateBetween(Long userId, LocalDate startDate, LocalDate endDate, Pageable pageable);

    @Query("SELECT t.category FROM Transaction t WHERE t.user.id = :userId AND t.category IS NOT NULL " +
            "GROUP BY t.category ORDER BY t.category")
    List<String> findDistinctCategories(@Param("userId") Long userId);

    @Query("SELECT COALESCE(t.category, 'Outros') FROM Transaction t " +
            "WHERE t.user.id = :userId AND t.type = 'EXPENSE' AND (t.neutral IS NULL OR t.neutral = false) " +
            "GROUP BY COALESCE(t.category, 'Outros') ORDER BY COALESCE(t.category, 'Outros')")
    List<String> findExpenseCategories(@Param("userId") Long userId);

    @Query("SELECT t FROM Transaction t " +
            "WHERE t.user.id = :userId AND t.type = 'EXPENSE' AND (t.neutral IS NULL OR t.neutral = false) " +
            "AND t.date BETWEEN :startDate AND :endDate ORDER BY t.date ASC, t.id ASC")
    List<Transaction> findCountedExpensesBetween(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    java.util.Optional<Transaction> findByIdAndUserId(Long id, Long userId);

    @Query("SELECT t FROM Transaction t " +
            "WHERE t.user.id = :userId AND (t.neutral IS NULL OR t.neutral = false) " +
            "AND (t.userEdited IS NULL OR t.userEdited = false) AND (t.manual IS NULL OR t.manual = false) " +
            "AND t.category LIKE 'Transfer%' ORDER BY t.date ASC, t.id ASC")
    List<Transaction> findTransferCandidates(@Param("userId") Long userId);

    List<Transaction> findByUserIdOrderByDateDescIdDesc(Long userId);

    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM Transaction t WHERE t.user.id = :userId")
    int deleteAllByUser(@Param("userId") Long userId);

    boolean existsByUserIdAndCategoryIsNull(Long userId);

    @Query("SELECT " +
            "SUM(CASE WHEN t.type = 'INCOME' THEN t.amount ELSE 0 END) as totalIncome, " +
            "SUM(CASE WHEN t.type = 'EXPENSE' THEN t.amount ELSE 0 END) as totalExpense " +
            "FROM Transaction t " +
            "WHERE t.user.id = :userId AND t.date BETWEEN :startDate AND :endDate " +
            "AND (t.neutral IS NULL OR t.neutral = false)")
    TransactionSummary getSummary(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("SELECT COALESCE(t.category, 'Outros') AS category, " +
            "SUM(t.amount) AS total, " +
            "COUNT(t) AS transactionCount " +
            "FROM Transaction t " +
            "WHERE t.user.id = :userId AND t.type = 'EXPENSE' AND t.date BETWEEN :startDate AND :endDate " +
            "AND (t.neutral IS NULL OR t.neutral = false) " +
            "GROUP BY COALESCE(t.category, 'Outros') " +
            "ORDER BY SUM(t.amount) ASC")
    List<CategoryTotal> getExpensesByCategory(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("SELECT YEAR(t.date) AS periodYear, MONTH(t.date) AS periodMonth, " +
            "SUM(CASE WHEN t.type = 'INCOME' THEN t.amount ELSE 0 END) AS income, " +
            "SUM(CASE WHEN t.type = 'EXPENSE' THEN t.amount ELSE 0 END) AS expense " +
            "FROM Transaction t " +
            "WHERE t.user.id = :userId AND t.date BETWEEN :startDate AND :endDate " +
            "AND (t.neutral IS NULL OR t.neutral = false) " +
            "GROUP BY YEAR(t.date), MONTH(t.date) " +
            "ORDER BY YEAR(t.date) ASC, MONTH(t.date) ASC")
    List<MonthTotal> getMonthlyTotals(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("SELECT t.date AS periodDate, " +
            "SUM(CASE WHEN t.type = 'INCOME' THEN t.amount ELSE 0 END) AS income, " +
            "SUM(CASE WHEN t.type = 'EXPENSE' THEN t.amount ELSE 0 END) AS expense " +
            "FROM Transaction t " +
            "WHERE t.user.id = :userId AND t.date BETWEEN :startDate AND :endDate " +
            "AND (t.neutral IS NULL OR t.neutral = false) " +
            "GROUP BY t.date " +
            "ORDER BY t.date ASC")
    List<DayTotal> getDailyTotals(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
            "WHERE t.user.id = :userId AND t.type = 'EXPENSE' AND t.paymentMethod = :method " +
            "AND t.date BETWEEN :startDate AND :endDate " +
            "AND (t.neutral IS NULL OR t.neutral = false)")
    BigDecimal sumExpensesByPaymentMethod(
            @Param("userId") Long userId,
            @Param("method") PaymentMethod method,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("SELECT t.externalId FROM Transaction t WHERE t.user.id = :userId AND t.externalId IN :externalIds")
    Set<String> findExistingExternalIds(
            @Param("userId") Long userId,
            @Param("externalIds") Collection<String> externalIds
    );
}
