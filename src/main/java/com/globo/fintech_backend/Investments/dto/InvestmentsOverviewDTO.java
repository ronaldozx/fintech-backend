package com.globo.fintech_backend.Investments.dto;

import java.math.BigDecimal;
import java.util.List;

public record InvestmentsOverviewDTO(
        List<InvestmentDTO> investments,
        BigDecimal totalBalance,
        BigDecimal totalInvested,
        BigDecimal totalProfit,
        List<AllocationDTO> allocation,
        List<InvestmentDTO> upcomingMaturities,
        ConcentrationDTO concentration,
        EmergencyFundDTO emergencyFund,
        List<String> unavailableConnections
) {}
