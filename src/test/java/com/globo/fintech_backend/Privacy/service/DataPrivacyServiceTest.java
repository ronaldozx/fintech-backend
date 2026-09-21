package com.globo.fintech_backend.Privacy.service;

import com.globo.fintech_backend.Auth.entity.User;
import com.globo.fintech_backend.Auth.repository.UserRepository;
import com.globo.fintech_backend.Budgets.entity.Budget;
import com.globo.fintech_backend.Budgets.repository.BudgetRepository;
import com.globo.fintech_backend.Goals.entity.Goal;
import com.globo.fintech_backend.Goals.entity.GoalContribution;
import com.globo.fintech_backend.Goals.repository.GoalContributionRepository;
import com.globo.fintech_backend.Goals.repository.GoalRepository;
import com.globo.fintech_backend.Notifications.repository.NotificationRepository;
import com.globo.fintech_backend.OpenFinance.connection.BankConnection;
import com.globo.fintech_backend.OpenFinance.connection.BankConnectionRepository;
import com.globo.fintech_backend.OpenFinance.provider.OpenFinanceProvider;
import com.globo.fintech_backend.Privacy.dto.AccountExportDTO;
import com.globo.fintech_backend.Privacy.dto.DeleteAccountResultDTO;
import com.globo.fintech_backend.Transactions.entity.Transaction;
import com.globo.fintech_backend.Transactions.enums.PaymentMethod;
import com.globo.fintech_backend.Transactions.enums.TransactionType;
import com.globo.fintech_backend.Transactions.repository.TransactionRepository;
import com.globo.fintech_backend.exception.BadRequestException;
import com.globo.fintech_backend.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataPrivacyServiceTest {

    private static final Long USER_ID = 7L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 21, 12, 0);

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private OpenFinanceProvider provider;

    @Mock
    private BankConnectionRepository connectionRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private BudgetRepository budgetRepository;

    @Mock
    private GoalRepository goalRepository;

    @Mock
    private GoalContributionRepository contributionRepository;

    @Mock
    private AccountPurger purger;

    private DataPrivacyService service;

    @BeforeEach
    void setUp() {
        service = new DataPrivacyService(userRepository, passwordEncoder, provider, connectionRepository,
                transactionRepository, budgetRepository, goalRepository, contributionRepository, purger);
    }

    private static User user() {
        User user = new User();
        user.setId(USER_ID);
        user.setEmail("ana@example.com");
        user.setPassword("hash");
        user.setFullName("Ana Souza");
        user.setMonthlyIncome(5000.0);
        return user;
    }

    private static BankConnection connection(String itemId) {
        BankConnection connection = new BankConnection();
        connection.setItemId(itemId);
        connection.setInstitutionName("Nubank");
        connection.setStatus("UPDATED");
        return connection;
    }

    @Test
    void exportsEverythingTheUserOwnsWithoutThePasswordOrProviderIds() {
        Transaction transaction = new Transaction();
        transaction.setDate(LocalDate.of(2026, 9, 10));
        transaction.setDescription("Mercado");
        transaction.setAmount(new BigDecimal("-50.00"));
        transaction.setType(TransactionType.EXPENSE);
        transaction.setPaymentMethod(PaymentMethod.DEBIT);
        transaction.setCategory("Mercado");
        transaction.setNeutral(true);
        transaction.setManual(true);

        Budget budget = new Budget();
        budget.setCategory("Mercado");
        budget.setMonthlyLimit(new BigDecimal("500.00"));

        Goal goal = new Goal();
        goal.setId(3L);
        goal.setName("Viagem");
        goal.setTargetAmount(new BigDecimal("3000.00"));
        goal.setSavedAmount(new BigDecimal("400.00"));
        GoalContribution contribution = new GoalContribution();
        contribution.setGoal(goal);
        contribution.setAmount(new BigDecimal("400.00"));
        contribution.setDate(LocalDate.of(2026, 9, 1));

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(connectionRepository.findByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of(connection("secret-item")));
        when(transactionRepository.findByUserIdOrderByDateDescIdDesc(USER_ID)).thenReturn(List.of(transaction));
        when(budgetRepository.findByUserId(USER_ID)).thenReturn(List.of(budget));
        when(goalRepository.findByUserIdOrderByCreatedAtAscIdAsc(USER_ID)).thenReturn(List.of(goal));
        when(contributionRepository.findByGoalUserIdOrderByDateAscIdAsc(USER_ID)).thenReturn(List.of(contribution));

        AccountExportDTO export = service.export(USER_ID, NOW);

        assertEquals(NOW, export.exportedAt());
        assertEquals("ana@example.com", export.profile().email());
        assertEquals("Nubank", export.connections().get(0).institutionName());
        assertEquals(1, export.transactions().size());
        assertFalse(export.transactions().get(0).countsInTotals());
        assertTrue(export.transactions().get(0).manual());
        assertEquals("Mercado", export.budgets().get(0).category());
        assertEquals(1, export.goals().get(0).contributions().size());
        assertFalse(export.toString().contains("hash"));
        assertFalse(export.toString().contains("secret-item"));
    }

    @Test
    void aGoalWithoutContributionsExportsAnEmptyList() {
        Goal goal = new Goal();
        goal.setId(3L);
        goal.setName("Viagem");
        goal.setTargetAmount(BigDecimal.TEN);
        goal.setSavedAmount(BigDecimal.ZERO);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(connectionRepository.findByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of());
        when(transactionRepository.findByUserIdOrderByDateDescIdDesc(USER_ID)).thenReturn(List.of());
        when(budgetRepository.findByUserId(USER_ID)).thenReturn(List.of());
        when(goalRepository.findByUserIdOrderByCreatedAtAscIdAsc(USER_ID)).thenReturn(List.of(goal));
        when(contributionRepository.findByGoalUserIdOrderByDateAscIdAsc(USER_ID)).thenReturn(List.of());

        assertTrue(service.export(USER_ID, NOW).goals().get(0).contributions().isEmpty());
    }

    @Test
    void deletingRequiresTheCorrectPassword() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(passwordEncoder.matches("errada", "hash")).thenReturn(false);

        assertThrows(BadRequestException.class, () -> service.delete(USER_ID, "errada"));
        assertThrows(BadRequestException.class, () -> service.delete(USER_ID, ""));
        assertThrows(BadRequestException.class, () -> service.delete(USER_ID, null));
        verify(purger, never()).purge(USER_ID);
        verify(provider, never()).deleteItem("x");
    }

    @Test
    void deletingRemovesTheBankConnectionsAtTheProviderThenPurgesEverything() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(passwordEncoder.matches("certa", "hash")).thenReturn(true);
        when(connectionRepository.findByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of(connection("item-1"), connection("item-2")));

        DeleteAccountResultDTO result = service.delete(USER_ID, "certa");

        assertEquals(2, result.connectionsRemoved());
        assertEquals(0, result.providerItemsNotRemoved());
        InOrder order = inOrder(provider, purger);
        order.verify(provider).deleteItem("item-1");
        order.verify(provider).deleteItem("item-2");
        order.verify(purger).purge(USER_ID);
    }

    @Test
    void aProviderFailureIsReportedButDoesNotBlockDeletingTheAccount() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user()));
        when(passwordEncoder.matches("certa", "hash")).thenReturn(true);
        when(connectionRepository.findByUserIdOrderByCreatedAtDesc(USER_ID)).thenReturn(List.of(connection("item-1"), connection("item-2")));
        doThrow(new IllegalStateException("Pluggy fora do ar")).when(provider).deleteItem("item-1");

        DeleteAccountResultDTO result = service.delete(USER_ID, "certa");

        assertEquals(1, result.providerItemsNotRemoved());
        verify(provider).deleteItem("item-2");
        verify(purger).purge(USER_ID);
    }

    @Test
    void anUnknownUserIsNotFound() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.delete(USER_ID, "x"));
        assertThrows(ResourceNotFoundException.class, () -> service.export(USER_ID, NOW));
    }

    @Test
    void purgeDeletesDependentDataBeforeTheUser() {
        NotificationRepository notifications = org.mockito.Mockito.mock(NotificationRepository.class);
        AccountPurger realPurger = new AccountPurger(notifications, contributionRepository, goalRepository, budgetRepository,
                transactionRepository, connectionRepository, userRepository);

        realPurger.purge(USER_ID);

        InOrder order = inOrder(notifications, contributionRepository, goalRepository, budgetRepository, transactionRepository, connectionRepository, userRepository);
        order.verify(notifications).deleteAllByUser(USER_ID);
        order.verify(contributionRepository).deleteAllByUser(USER_ID);
        order.verify(goalRepository).deleteAllByUser(USER_ID);
        order.verify(budgetRepository).deleteAllByUser(USER_ID);
        order.verify(transactionRepository).deleteAllByUser(USER_ID);
        order.verify(connectionRepository).deleteAllByUser(USER_ID);
        order.verify(userRepository).deleteById(USER_ID);
    }
}
