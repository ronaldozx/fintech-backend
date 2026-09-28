package com.globo.fintech_backend.Budgets.repository;

import com.globo.fintech_backend.Budgets.entity.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, Long> {

    List<Budget> findByUserId(Long userId);

    Optional<Budget> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndCategory(Long userId, String category);

    boolean existsByUserIdAndCategoryIsNull(Long userId);

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM Budget b WHERE b.user.id = :userId")
    int deleteAllByUser(@Param("userId") Long userId);
}
