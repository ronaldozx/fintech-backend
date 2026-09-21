package com.globo.fintech_backend.Goals.repository;

import com.globo.fintech_backend.Goals.entity.Goal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GoalRepository extends JpaRepository<Goal, Long> {

    List<Goal> findByUserIdOrderByCreatedAtAscIdAsc(Long userId);

    Optional<Goal> findByIdAndUserId(Long id, Long userId);

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM Goal g WHERE g.user.id = :userId")
    int deleteAllByUser(@Param("userId") Long userId);
}
