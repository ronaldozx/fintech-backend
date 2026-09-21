package com.globo.fintech_backend.Goals.repository;

import com.globo.fintech_backend.Goals.entity.GoalContribution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface GoalContributionRepository extends JpaRepository<GoalContribution, Long> {

    @Transactional
    void deleteByGoalId(Long goalId);

    List<GoalContribution> findByGoalUserIdOrderByDateAscIdAsc(Long userId);

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM GoalContribution c WHERE c.goal.id IN (SELECT g.id FROM Goal g WHERE g.user.id = :userId)")
    int deleteAllByUser(@Param("userId") Long userId);
}
