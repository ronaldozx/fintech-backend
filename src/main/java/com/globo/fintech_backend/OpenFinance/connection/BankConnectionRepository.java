package com.globo.fintech_backend.OpenFinance.connection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BankConnectionRepository extends JpaRepository<BankConnection, Long> {

    List<BankConnection> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<BankConnection> findByIdAndUserId(Long id, Long userId);

    Optional<BankConnection> findByItemId(String itemId);

    boolean existsByUserIdAndInstitutionName(Long userId, String institutionName);
}
