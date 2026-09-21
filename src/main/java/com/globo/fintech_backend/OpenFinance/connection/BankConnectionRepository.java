package com.globo.fintech_backend.OpenFinance.connection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BankConnectionRepository extends JpaRepository<BankConnection, Long> {

    List<BankConnection> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<BankConnection> findByIdAndUserId(Long id, Long userId);

    Optional<BankConnection> findByItemId(String itemId);

    @Transactional
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM BankConnection c WHERE c.user.id = :userId")
    int deleteAllByUser(@Param("userId") Long userId);
}
