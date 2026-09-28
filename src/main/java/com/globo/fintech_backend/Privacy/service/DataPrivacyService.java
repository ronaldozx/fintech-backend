package com.globo.fintech_backend.Privacy.service;

import com.globo.fintech_backend.Auth.entity.User;
import com.globo.fintech_backend.Auth.repository.UserRepository;
import com.globo.fintech_backend.Budgets.repository.BudgetRepository;
import com.globo.fintech_backend.Goals.entity.Goal;
import com.globo.fintech_backend.Goals.entity.GoalContribution;
import com.globo.fintech_backend.Goals.repository.GoalContributionRepository;
import com.globo.fintech_backend.Goals.repository.GoalRepository;
import com.globo.fintech_backend.OpenFinance.connection.BankConnection;
import com.globo.fintech_backend.OpenFinance.connection.BankConnectionRepository;
import com.globo.fintech_backend.OpenFinance.provider.OpenFinanceProvider;
import com.globo.fintech_backend.Privacy.dto.AccountExportDTO;
import com.globo.fintech_backend.Privacy.dto.DeleteAccountResultDTO;
import com.globo.fintech_backend.Transactions.repository.TransactionRepository;
import com.globo.fintech_backend.exception.BadRequestException;
import com.globo.fintech_backend.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DataPrivacyService {

    private static final Logger log = LoggerFactory.getLogger(DataPrivacyService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final OpenFinanceProvider provider;
    private final BankConnectionRepository connectionRepository;
    private final TransactionRepository transactionRepository;
    private final BudgetRepository budgetRepository;
    private final GoalRepository goalRepository;
    private final GoalContributionRepository contributionRepository;
    private final AccountPurger purger;

    public DataPrivacyService(UserRepository userRepository,
                              PasswordEncoder passwordEncoder,
                              OpenFinanceProvider provider,
                              BankConnectionRepository connectionRepository,
                              TransactionRepository transactionRepository,
                              BudgetRepository budgetRepository,
                              GoalRepository goalRepository,
                              GoalContributionRepository contributionRepository,
                              AccountPurger purger) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.provider = provider;
        this.connectionRepository = connectionRepository;
        this.transactionRepository = transactionRepository;
        this.budgetRepository = budgetRepository;
        this.goalRepository = goalRepository;
        this.contributionRepository = contributionRepository;
        this.purger = purger;
    }

    @Transactional(readOnly = true)
    public AccountExportDTO export(Long userId, LocalDateTime now) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        Map<Long, List<GoalContribution>> contributions = contributionRepository.findByGoalUserIdOrderByDateAscIdAsc(userId).stream()
                .collect(Collectors.groupingBy(contribution -> contribution.getGoal().getId()));

        return new AccountExportDTO(
                now,
                new AccountExportDTO.Profile(user.getEmail(), user.getFullName(), user.getBirthDate(), user.getMonthlyIncome(), user.getCreatedAt()),
                connectionRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                        .map(connection -> new AccountExportDTO.Connection(
                                connection.getInstitutionName(), connection.getStatus(), connection.getCreatedAt(), connection.getLastSyncedAt()))
                        .toList(),
                transactionRepository.findByUserIdOrderByDateDescIdDesc(userId).stream()
                        .map(transaction -> new AccountExportDTO.TransactionItem(
                                transaction.getDate(),
                                transaction.getDescription(),
                                transaction.getAmount(),
                                String.valueOf(transaction.getType()),
                                transaction.getCategory(),
                                String.valueOf(transaction.getPaymentMethod()),
                                !Boolean.TRUE.equals(transaction.getNeutral()),
                                Boolean.TRUE.equals(transaction.getManual())))
                        .toList(),
                budgetRepository.findByUserId(userId).stream()
                        .map(budget -> new AccountExportDTO.Budget(budget.getCategory(), budget.getMonthlyLimit()))
                        .toList(),
                goalRepository.findByUserIdOrderByCreatedAtAscIdAsc(userId).stream()
                        .map(goal -> toExport(goal, contributions.getOrDefault(goal.getId(), List.of())))
                        .toList());
    }

    public DeleteAccountResultDTO delete(Long userId, String password) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        if (password == null || password.isBlank() || !passwordEncoder.matches(password, user.getPassword())) {
            throw new BadRequestException("Senha incorreta");
        }

        List<BankConnection> connections = connectionRepository.findByUserIdOrderByCreatedAtDesc(userId);
        int notRemoved = 0;
        for (BankConnection connection : connections) {
            try {
                provider.deleteItem(connection.getItemId());
            } catch (RuntimeException e) {
                notRemoved++;
                log.warn("Could not remove item {} at the provider: {}", connection.getId(), e.getMessage());
            }
        }

        purger.purge(userId);
        return new DeleteAccountResultDTO(connections.size(), notRemoved);
    }

    private static AccountExportDTO.Goal toExport(Goal goal, List<GoalContribution> contributions) {
        return new AccountExportDTO.Goal(
                goal.getName(),
                goal.getTargetAmount(),
                goal.getSavedAmount(),
                goal.getTargetDate(),
                contributions.stream().map(contribution -> new AccountExportDTO.Contribution(contribution.getDate(), contribution.getAmount())).toList());
    }
}
