package com.globo.fintech_backend.Privacy.service;

import com.globo.fintech_backend.Auth.entity.User;
import com.globo.fintech_backend.Auth.repository.UserRepository;
import com.globo.fintech_backend.Goals.entity.Goal;
import com.globo.fintech_backend.Goals.repository.GoalRepository;
import com.globo.fintech_backend.OpenFinance.connection.BankConnection;
import com.globo.fintech_backend.OpenFinance.connection.BankConnectionRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest
@Transactional
class AccountPurgerIntegrationTest {

    @Autowired
    private AccountPurger purger;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BankConnectionRepository connectionRepository;

    @Autowired
    private GoalRepository goalRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void purgesAUserWhoseDataIsAlreadyLoadedInThePersistenceContext() {
        User user = new User();
        user.setEmail("purger-" + UUID.randomUUID() + "@example.com");
        user.setPassword("hash");
        user = userRepository.save(user);

        BankConnection connection = new BankConnection();
        connection.setUser(user);
        connection.setItemId("purger-item-" + UUID.randomUUID());
        connectionRepository.save(connection);

        Goal goal = new Goal();
        goal.setUser(user);
        goal.setName("Meta de teste");
        goal.setTargetAmount(new BigDecimal("100.00"));
        goalRepository.save(goal);
        entityManager.flush();
        entityManager.clear();

        Long userId = user.getId();
        userRepository.findById(userId);
        assertEquals(1, connectionRepository.findByUserIdOrderByCreatedAtDesc(userId).size());
        assertEquals(1, goalRepository.findByUserIdOrderByCreatedAtAscIdAsc(userId).size());

        purger.purge(userId);
        entityManager.flush();

        assertFalse(userRepository.existsById(userId));
        assertEquals(0, connectionRepository.findByUserIdOrderByCreatedAtDesc(userId).size());
        assertEquals(0, goalRepository.findByUserIdOrderByCreatedAtAscIdAsc(userId).size());
    }
}
