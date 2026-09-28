package com.globo.fintech_backend.Budgets.service;

import com.globo.fintech_backend.Auth.entity.User;
import com.globo.fintech_backend.Auth.repository.UserRepository;
import com.globo.fintech_backend.Budgets.dto.BudgetDTO;
import com.globo.fintech_backend.Budgets.dto.BudgetProgressDTO;
import com.globo.fintech_backend.Budgets.dto.BudgetRequestDTO;
import com.globo.fintech_backend.Budgets.dto.BudgetStatus;
import com.globo.fintech_backend.Budgets.dto.BudgetUpdateDTO;
import com.globo.fintech_backend.Budgets.dto.BudgetsOverviewDTO;
import com.globo.fintech_backend.Budgets.dto.CategorySpendDTO;
import com.globo.fintech_backend.Budgets.entity.Budget;
import com.globo.fintech_backend.Budgets.repository.BudgetRepository;
import com.globo.fintech_backend.Transactions.repository.CategoryTotal;
import com.globo.fintech_backend.common.MonthParser;
import com.globo.fintech_backend.Transactions.repository.TransactionRepository;
import com.globo.fintech_backend.exception.BadRequestException;
import com.globo.fintech_backend.exception.ConflictException;
import com.globo.fintech_backend.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class BudgetService {

    static final int WARNING_PERCENT = 80;
    static final int EXCEEDED_PERCENT = 100;
    private static final int PERCENT_CAP = 999;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final BudgetRepository budgetRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    public BudgetService(BudgetRepository budgetRepository,
                         TransactionRepository transactionRepository,
                         UserRepository userRepository) {
        this.budgetRepository = budgetRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public BudgetsOverviewDTO overview(Long userId, String monthText, YearMonth today) {
        YearMonth month = MonthParser.parse(monthText, today);

        Map<String, BigDecimal> spentByCategory = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (CategoryTotal row : transactionRepository.getExpensesByCategory(
                userId, month.atDay(1), month.atEndOfMonth())) {
            spentByCategory.merge(row.getCategory(), row.getTotal().abs(), BigDecimal::add);
        }

        BigDecimal totalSpent = spentByCategory.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Budget> budgets = budgetRepository.findByUserId(userId);

        List<BudgetProgressDTO> progress = budgets.stream()
                .map(budget -> toProgress(budget,
                        budget.getCategory() == null
                                ? totalSpent
                                : spentByCategory.getOrDefault(budget.getCategory(), BigDecimal.ZERO)))
                .sorted(Comparator
                        .comparing((BudgetProgressDTO b) -> b.category() != null)
                        .thenComparing(Comparator.comparingInt(BudgetProgressDTO::percent).reversed())
                        .thenComparing(BudgetProgressDTO::category, Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER)))
                .toList();

        TreeMap<String, BigDecimal> remaining = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        remaining.putAll(spentByCategory);
        budgets.stream().map(Budget::getCategory).filter(c -> c != null).forEach(remaining::remove);

        List<CategorySpendDTO> unbudgeted = remaining.entrySet().stream()
                .map(entry -> new CategorySpendDTO(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(CategorySpendDTO::spent).reversed())
                .toList();

        return new BudgetsOverviewDTO(month.toString(), progress, unbudgeted, totalSpent);
    }

    @Transactional(readOnly = true)
    public List<String> categories(Long userId) {
        return transactionRepository.findExpenseCategories(userId);
    }

    @Transactional
    public BudgetDTO create(Long userId, BudgetRequestDTO request) {
        BigDecimal limit = validLimit(request.monthlyLimit());
        String category = normalizeCategory(request.category());

        boolean duplicated = category == null
                ? budgetRepository.existsByUserIdAndCategoryIsNull(userId)
                : budgetRepository.existsByUserIdAndCategory(userId, category);
        if (duplicated) {
            throw new ConflictException(category == null
                    ? "Já existe um orçamento total definido"
                    : "Já existe um orçamento para a categoria " + category);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        Budget budget = new Budget();
        budget.setUser(user);
        budget.setCategory(category);
        budget.setMonthlyLimit(limit);

        return toDto(budgetRepository.save(budget));
    }

    @Transactional
    public BudgetDTO update(Long userId, Long id, BudgetUpdateDTO request) {
        BigDecimal limit = validLimit(request.monthlyLimit());
        Budget budget = budgetRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Orçamento não encontrado"));

        budget.setMonthlyLimit(limit);

        return toDto(budgetRepository.save(budget));
    }

    @Transactional
    public void delete(Long userId, Long id) {
        Budget budget = budgetRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Orçamento não encontrado"));

        budgetRepository.delete(budget);
    }

    static BudgetStatus statusFor(int percent) {
        if (percent >= EXCEEDED_PERCENT) {
            return BudgetStatus.EXCEEDED;
        }
        if (percent >= WARNING_PERCENT) {
            return BudgetStatus.WARNING;
        }
        return BudgetStatus.OK;
    }

    private BudgetDTO toDto(Budget budget) {
        return new BudgetDTO(budget.getId(), budget.getCategory(), budget.getMonthlyLimit());
    }

    private BudgetProgressDTO toProgress(Budget budget, BigDecimal spent) {
        BigDecimal limit = budget.getMonthlyLimit();
        int percent = spent.multiply(HUNDRED).divide(limit, 0, RoundingMode.DOWN)
                .min(BigDecimal.valueOf(PERCENT_CAP)).intValue();

        return new BudgetProgressDTO(
                budget.getId(),
                budget.getCategory(),
                limit,
                spent,
                limit.subtract(spent),
                percent,
                statusFor(percent)
        );
    }

    private BigDecimal validLimit(BigDecimal limit) {
        if (limit == null || limit.signum() <= 0) {
            throw new BadRequestException("O limite mensal deve ser maior que zero");
        }
        return limit.setScale(2, RoundingMode.HALF_UP);
    }

    private String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return null;
        }
        return category.trim();
    }
}
