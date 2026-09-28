package com.globo.fintech_backend.Transactions.service;

import com.globo.fintech_backend.Transactions.dto.CategorySummaryDTO;
import com.globo.fintech_backend.Transactions.dto.DailySummaryDTO;
import com.globo.fintech_backend.Transactions.dto.MonthlySummaryDTO;
import com.globo.fintech_backend.Transactions.dto.TransactionDTO;
import com.globo.fintech_backend.Transactions.dto.TransactionDashboardDTO;
import com.globo.fintech_backend.Transactions.repository.TransactionRepository;
import com.globo.fintech_backend.Transactions.repository.TransactionSummary;
import com.globo.fintech_backend.exception.BadRequestException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;

    public TransactionService(TransactionRepository transactionRepository){
        this.transactionRepository = transactionRepository;
    }

    public TransactionDashboardDTO getDashboardData(Long userId, LocalDate startDate, LocalDate endDate, Pageable pageable){
        requireValidRange(startDate, endDate);
        Page<TransactionDTO> page = transactionRepository.findByUserIdAndDateBetween(userId, startDate, endDate, pageable);
        TransactionSummary summary = transactionRepository.getSummary(userId, startDate, endDate);
        return new TransactionDashboardDTO(summary, page);
    }

    public List<CategorySummaryDTO> getExpensesByCategory(Long userId, LocalDate startDate, LocalDate endDate){
        requireValidRange(startDate, endDate);
        return transactionRepository.getExpensesByCategory(userId, startDate, endDate).stream()
                .map(total -> new CategorySummaryDTO(total.getCategory(), total.getTotal().abs(), total.getTransactionCount()))
                .toList();
    }

    public List<MonthlySummaryDTO> getMonthlySummary(Long userId, LocalDate startDate, LocalDate endDate){
        requireValidRange(startDate, endDate);
        return transactionRepository.getMonthlyTotals(userId, startDate, endDate).stream()
                .map(total -> new MonthlySummaryDTO(
                        String.format("%04d-%02d", total.getPeriodYear(), total.getPeriodMonth()),
                        orZero(total.getIncome()),
                        orZero(total.getExpense()).abs()))
                .toList();
    }

    public List<DailySummaryDTO> getDailySummary(Long userId, LocalDate startDate, LocalDate endDate){
        requireValidRange(startDate, endDate);
        return transactionRepository.getDailyTotals(userId, startDate, endDate).stream()
                .map(total -> new DailySummaryDTO(
                        total.getPeriodDate(),
                        orZero(total.getIncome()),
                        orZero(total.getExpense()).abs()))
                .toList();
    }

    private static BigDecimal orZero(BigDecimal value){
        return value == null ? BigDecimal.ZERO : value;
    }

    private static void requireValidRange(LocalDate startDate, LocalDate endDate){
        if (startDate.isAfter(endDate)) {
            throw new BadRequestException("startDate deve ser anterior ou igual a endDate");
        }
    }
}
