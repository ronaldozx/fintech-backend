package com.globo.fintech_backend.Privacy.service;

import com.globo.fintech_backend.Auth.repository.UserRepository;
import com.globo.fintech_backend.Budgets.repository.BudgetRepository;
import com.globo.fintech_backend.Goals.repository.GoalContributionRepository;
import com.globo.fintech_backend.Goals.repository.GoalRepository;
import com.globo.fintech_backend.Notifications.repository.NotificationRepository;
import com.globo.fintech_backend.OpenFinance.connection.BankConnectionRepository;
import com.globo.fintech_backend.Transactions.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountPurger {

    private final NotificationRepository notificationRepository;
    private final GoalContributionRepository contributionRepository;
    private final GoalRepository goalRepository;
    private final BudgetRepository budgetRepository;
    private final TransactionRepository transactionRepository;
    private final BankConnectionRepository connectionRepository;
    private final UserRepository userRepository;

    public AccountPurger(NotificationRepository notificationRepository,
                         GoalContributionRepository contributionRepository,
                         GoalRepository goalRepository,
                         BudgetRepository budgetRepository,
                         TransactionRepository transactionRepository,
                         BankConnectionRepository connectionRepository,
                         UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.contributionRepository = contributionRepository;
        this.goalRepository = goalRepository;
        this.budgetRepository = budgetRepository;
        this.transactionRepository = transactionRepository;
        this.connectionRepository = connectionRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void purge(Long userId) {
        notificationRepository.deleteAllByUser(userId);
        contributionRepository.deleteAllByUser(userId);
        goalRepository.deleteAllByUser(userId);
        budgetRepository.deleteAllByUser(userId);
        transactionRepository.deleteAllByUser(userId);
        connectionRepository.deleteAllByUser(userId);
        userRepository.deleteById(userId);
    }
}
