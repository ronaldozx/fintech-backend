package com.globo.fintech_backend.Goals.service;

import com.globo.fintech_backend.Auth.entity.User;
import com.globo.fintech_backend.Auth.repository.UserRepository;
import com.globo.fintech_backend.Goals.dto.GoalDTO;
import com.globo.fintech_backend.Goals.dto.GoalRequestDTO;
import com.globo.fintech_backend.Goals.dto.GoalsOverviewDTO;
import com.globo.fintech_backend.Goals.entity.Goal;
import com.globo.fintech_backend.Goals.entity.GoalContribution;
import com.globo.fintech_backend.Goals.repository.GoalContributionRepository;
import com.globo.fintech_backend.Goals.repository.GoalRepository;
import com.globo.fintech_backend.Transactions.repository.MonthTotal;
import com.globo.fintech_backend.Transactions.repository.TransactionRepository;
import com.globo.fintech_backend.exception.BadRequestException;
import com.globo.fintech_backend.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Service
public class GoalService {

    static final int SAVINGS_MONTHS = 3;
    static final int MAX_NAME = 100;
    private static final int MAX_YEARS_AHEAD = 50;
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("1000000000000");

    private final GoalRepository goalRepository;
    private final GoalContributionRepository contributionRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;

    public GoalService(GoalRepository goalRepository,
                       GoalContributionRepository contributionRepository,
                       UserRepository userRepository,
                       TransactionRepository transactionRepository) {
        this.goalRepository = goalRepository;
        this.contributionRepository = contributionRepository;
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional(readOnly = true)
    public GoalsOverviewDTO overview(Long userId, LocalDate today) {
        BigDecimal average = averageMonthlySavings(userId, today);

        List<GoalDTO> goals = goalRepository.findByUserIdOrderByCreatedAtAscIdAsc(userId).stream()
                .map(goal -> GoalMath.toDto(goal, today, average))
                .toList();

        return new GoalsOverviewDTO(
                goals,
                goals.stream().map(GoalDTO::targetAmount).reduce(BigDecimal.ZERO, BigDecimal::add),
                goals.stream().map(GoalDTO::savedAmount).reduce(BigDecimal.ZERO, BigDecimal::add),
                average);
    }

    @Transactional
    public GoalDTO create(Long userId, GoalRequestDTO request, LocalDate today) {
        String name = validName(request.name());
        BigDecimal target = validAmount(request.targetAmount());
        LocalDate date = validDate(request.targetDate(), null, today);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        Goal goal = new Goal();
        goal.setUser(user);
        goal.setName(name);
        goal.setTargetAmount(target);
        goal.setSavedAmount(BigDecimal.ZERO);
        goal.setTargetDate(date);

        return GoalMath.toDto(goalRepository.save(goal), today, averageMonthlySavings(userId, today));
    }

    @Transactional
    public GoalDTO update(Long userId, Long id, GoalRequestDTO request, LocalDate today) {
        String name = validName(request.name());
        BigDecimal target = validAmount(request.targetAmount());
        Goal goal = find(userId, id);
        LocalDate date = validDate(request.targetDate(), goal.getTargetDate(), today);

        goal.setName(name);
        goal.setTargetAmount(target);
        goal.setTargetDate(date);

        return GoalMath.toDto(goalRepository.save(goal), today, averageMonthlySavings(userId, today));
    }

    @Transactional
    public void delete(Long userId, Long id) {
        Goal goal = find(userId, id);

        contributionRepository.deleteByGoalId(goal.getId());
        goalRepository.delete(goal);
    }

    @Transactional
    public GoalDTO contribute(Long userId, Long id, BigDecimal amount, LocalDate today) {
        if (amount == null || amount.signum() == 0) {
            throw new BadRequestException("Informe um valor diferente de zero");
        }
        if (amount.abs().compareTo(MAX_AMOUNT) > 0) {
            throw new BadRequestException("Valor grande demais");
        }

        Goal goal = find(userId, id);
        BigDecimal value = amount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal updated = goal.getSavedAmount().add(value);

        if (updated.signum() < 0) {
            throw new BadRequestException("Você não pode retirar mais do que já guardou");
        }

        GoalContribution contribution = new GoalContribution();
        contribution.setGoal(goal);
        contribution.setAmount(value);
        contribution.setDate(today);
        contributionRepository.save(contribution);

        goal.setSavedAmount(updated);
        return GoalMath.toDto(goalRepository.save(goal), today, averageMonthlySavings(userId, today));
    }

    BigDecimal averageMonthlySavings(Long userId, LocalDate today) {
        YearMonth current = YearMonth.from(today);
        LocalDate start = current.minusMonths(SAVINGS_MONTHS).atDay(1);
        LocalDate end = current.minusMonths(1).atEndOfMonth();

        List<MonthTotal> months = transactionRepository.getMonthlyTotals(userId, start, end);
        if (months.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal total = months.stream()
                .map(month -> month.getIncome().subtract(month.getExpense().abs()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return total.divide(BigDecimal.valueOf(months.size()), 2, RoundingMode.HALF_UP);
    }

    private Goal find(Long userId, Long id) {
        return goalRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Meta não encontrada"));
    }

    private static String validName(String name) {
        if (name == null || name.isBlank()) {
            throw new BadRequestException("Informe o nome da meta");
        }
        String trimmed = name.trim();
        if (trimmed.length() > MAX_NAME) {
            throw new BadRequestException("O nome pode ter no máximo " + MAX_NAME + " caracteres");
        }
        return trimmed;
    }

    private static BigDecimal validAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BadRequestException("O valor da meta deve ser maior que zero");
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw new BadRequestException("Valor grande demais");
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private static LocalDate validDate(LocalDate date, LocalDate current, LocalDate today) {
        if (date == null) {
            return null;
        }
        if (date.isBefore(today) && !date.equals(current)) {
            throw new BadRequestException("A data da meta não pode estar no passado");
        }
        if (date.isAfter(today.plusYears(MAX_YEARS_AHEAD))) {
            throw new BadRequestException("Data distante demais");
        }
        return date;
    }
}
