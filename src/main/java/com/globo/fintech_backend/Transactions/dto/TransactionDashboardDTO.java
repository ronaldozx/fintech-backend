package com.globo.fintech_backend.Transactions.dto;

import com.globo.fintech_backend.Transactions.repository.TransactionSummary;
import org.springframework.data.domain.Page;
import lombok.Getter;
import java.math.BigDecimal;

@Getter
public class TransactionDashboardDTO {
    private BigDecimal totalIncome;
    private BigDecimal totalExpense;
    private BigDecimal netBalance; // O Saldo Líquido
    private Page<TransactionDTO> transactions;

    public TransactionDashboardDTO(TransactionSummary summary, Page<TransactionDTO> transactions) {
        this.totalIncome = summary.getTotalIncome() != null ? summary.getTotalIncome() : BigDecimal.ZERO;
        this.totalExpense = summary.getTotalExpense() != null ? summary.getTotalExpense().abs() : BigDecimal.ZERO;

        this.netBalance = this.totalIncome.subtract(this.totalExpense);

        this.transactions = transactions;
    }
}
