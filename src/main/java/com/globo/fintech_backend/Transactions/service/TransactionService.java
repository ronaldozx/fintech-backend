package com.globo.fintech_backend.Transactions.service;

import com.globo.fintech_backend.Transactions.dto.TransactionDTO;
import com.globo.fintech_backend.Transactions.dto.TransactionDashboardDTO;
import com.globo.fintech_backend.Transactions.repository.TransactionRepository;
import com.globo.fintech_backend.Transactions.repository.TransactionSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;

    public TransactionService(TransactionRepository transactionRepository){
        this.transactionRepository = transactionRepository;
    }

    public TransactionDashboardDTO getDashboardData(Long userId, LocalDate startDate, LocalDate endDate, Pageable pageable){
        Page<TransactionDTO> page = transactionRepository.findByUserIdAndDateBetween(userId, startDate, endDate, pageable);
        TransactionSummary summary = transactionRepository.getSummary(userId, startDate, endDate);
        return new TransactionDashboardDTO(summary, page);
    }
}
