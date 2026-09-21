package com.globo.fintech_backend.Goals.repository;

import com.globo.fintech_backend.Goals.entity.GoalContribution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface GoalContributionRepository extends JpaRepository<GoalContribution, Long> {

    @Transactional
    void deleteByGoalId(Long goalId);
}
