package com.globo.fintech_backend.Investments.service;

import com.globo.fintech_backend.Investments.dto.AllocationDTO;
import com.globo.fintech_backend.Investments.dto.ConcentrationDTO;
import com.globo.fintech_backend.Investments.dto.EmergencyFundDTO;
import com.globo.fintech_backend.Investments.dto.InvestmentDTO;
import com.globo.fintech_backend.Investments.dto.InvestmentsOverviewDTO;
import com.globo.fintech_backend.OpenFinance.accounts.AccountsService;
import com.globo.fintech_backend.OpenFinance.connection.BankConnection;
import com.globo.fintech_backend.OpenFinance.connection.BankConnectionRepository;
import com.globo.fintech_backend.OpenFinance.provider.OpenFinanceProvider;
import com.globo.fintech_backend.OpenFinance.provider.ProviderInvestment;
import com.globo.fintech_backend.Transactions.repository.MonthTotal;
import com.globo.fintech_backend.Transactions.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class InvestmentsService {

    static final int MATURITY_DAYS = 90;
    static final int REFERENCE_MONTHS = 6;
    static final int EXPENSE_MONTHS = 3;
    static final int CONCENTRATION_PERCENT = 50;

    private static final Logger log = LoggerFactory.getLogger(InvestmentsService.class);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final String OTHERS = "Outros";
    private static final Map<String, String> TYPE_LABELS = Map.of(
            "FIXED_INCOME", "Renda fixa",
            "MUTUAL_FUND", "Fundos",
            "EQUITY", "Ações",
            "ETF", "ETFs",
            "COE", "COE");

    private final BankConnectionRepository connectionRepository;
    private final OpenFinanceProvider provider;
    private final TransactionRepository transactionRepository;
    private final AccountsService accountsService;

    public InvestmentsService(BankConnectionRepository connectionRepository,
                              OpenFinanceProvider provider,
                              TransactionRepository transactionRepository,
                              AccountsService accountsService) {
        this.connectionRepository = connectionRepository;
        this.provider = provider;
        this.transactionRepository = transactionRepository;
        this.accountsService = accountsService;
    }

    public InvestmentsOverviewDTO overview(Long userId, LocalDate today) {
        List<InvestmentDTO> investments = new ArrayList<>();
        List<String> unavailable = new ArrayList<>();

        for (BankConnection connection : connectionRepository.findByUserIdOrderByCreatedAtDesc(userId)) {
            String institution = connection.getInstitutionName() == null ? "Banco" : connection.getInstitutionName();
            try {
                provider.listInvestments(connection.getItemId()).stream()
                        .filter(investment -> investment.balance() != null && investment.balance().signum() > 0)
                        .map(investment -> toDto(investment, institution, today))
                        .forEach(investments::add);
            } catch (RuntimeException e) {
                log.warn("Could not read the investments of connection {}: {}", connection.getId(), e.getMessage());
                unavailable.add(institution);
            }
        }

        investments.sort(Comparator.comparing(InvestmentDTO::balance).reversed());
        BigDecimal totalBalance = sum(investments, InvestmentDTO::balance);

        List<InvestmentDTO> withCost = investments.stream().filter(investment -> investment.invested() != null).toList();
        BigDecimal totalInvested = withCost.isEmpty() ? null : sum(withCost, InvestmentDTO::invested);
        BigDecimal totalProfit = withCost.isEmpty() ? null : sum(withCost, InvestmentDTO::balance).subtract(totalInvested);

        return new InvestmentsOverviewDTO(
                investments,
                totalBalance,
                totalInvested,
                totalProfit,
                allocation(investments, totalBalance),
                maturities(investments, today),
                concentration(investments, totalBalance),
                emergencyFund(userId, today, totalBalance),
                unavailable);
    }

    static InvestmentDTO toDto(ProviderInvestment source, String institution, LocalDate today) {
        BigDecimal profit = source.invested() == null ? null : source.balance().subtract(source.invested());
        BigDecimal profitPercent = profit != null && source.invested().signum() > 0
                ? profit.multiply(HUNDRED).divide(source.invested(), 2, RoundingMode.HALF_UP)
                : null;

        return new InvestmentDTO(
                source.id(),
                source.name(),
                source.type(),
                typeLabel(source.type()),
                institution,
                source.issuer(),
                source.balance(),
                source.invested(),
                profit,
                profitPercent,
                source.dueDate(),
                source.dueDate() == null ? null : ChronoUnit.DAYS.between(today, source.dueDate()),
                source.rate(),
                source.rateType());
    }

    static String typeLabel(String type) {
        return type == null ? OTHERS : TYPE_LABELS.getOrDefault(type, OTHERS);
    }

    static List<AllocationDTO> allocation(List<InvestmentDTO> investments, BigDecimal totalBalance) {
        if (totalBalance.signum() <= 0) {
            return List.of();
        }

        Map<String, BigDecimal> byLabel = new LinkedHashMap<>();
        investments.forEach(investment -> byLabel.merge(investment.typeLabel(), investment.balance(), BigDecimal::add));

        return byLabel.entrySet().stream()
                .map(entry -> new AllocationDTO(entry.getKey(), entry.getValue(), percent(entry.getValue(), totalBalance)))
                .sorted(Comparator.comparing(AllocationDTO::total).reversed())
                .toList();
    }

    static List<InvestmentDTO> maturities(List<InvestmentDTO> investments, LocalDate today) {
        return investments.stream()
                .filter(investment -> investment.dueDate() != null
                        && !investment.dueDate().isBefore(today)
                        && investment.daysToDue() <= MATURITY_DAYS)
                .sorted(Comparator.comparing(InvestmentDTO::dueDate))
                .toList();
    }

    static ConcentrationDTO concentration(List<InvestmentDTO> investments, BigDecimal totalBalance) {
        if (totalBalance.signum() <= 0) {
            return null;
        }

        Map<String, BigDecimal> byIssuer = new LinkedHashMap<>();
        investments.forEach(investment -> byIssuer.merge(
                investment.issuer() == null || investment.issuer().isBlank() ? investment.institution() : investment.issuer(),
                investment.balance(),
                BigDecimal::add));

        return byIssuer.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(entry -> new ConcentrationDTO(entry.getKey(), percent(entry.getValue(), totalBalance)))
                .filter(concentration -> concentration.percent() >= CONCENTRATION_PERCENT)
                .orElse(null);
    }

    private EmergencyFundDTO emergencyFund(Long userId, LocalDate today, BigDecimal investmentsBalance) {
        BigDecimal average = averageMonthlyExpenses(userId, today);

        BigDecimal available = investmentsBalance;
        boolean includesAccounts = false;
        try {
            BigDecimal bankBalance = accountsService.overview(userId, today).bankBalance();
            available = available.add(bankBalance.max(BigDecimal.ZERO));
            includesAccounts = true;
        } catch (RuntimeException e) {
            log.warn("Could not read the account balances for user {}: {}", userId, e.getMessage());
        }

        BigDecimal covered = average.signum() > 0 ? available.divide(average, 1, RoundingMode.DOWN) : null;

        return new EmergencyFundDTO(
                average,
                REFERENCE_MONTHS,
                average.multiply(BigDecimal.valueOf(REFERENCE_MONTHS)).setScale(2, RoundingMode.HALF_UP),
                available,
                includesAccounts,
                covered);
    }

    BigDecimal averageMonthlyExpenses(Long userId, LocalDate today) {
        YearMonth current = YearMonth.from(today);
        List<MonthTotal> months = transactionRepository.getMonthlyTotals(
                userId, current.minusMonths(EXPENSE_MONTHS).atDay(1), current.minusMonths(1).atEndOfMonth());

        if (months.isEmpty()) {
            return BigDecimal.ZERO.setScale(2);
        }

        return months.stream()
                .map(month -> month.getExpense().abs())
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(months.size()), 2, RoundingMode.HALF_UP);
    }

    private static int percent(BigDecimal part, BigDecimal total) {
        return part.multiply(HUNDRED).divide(total, 0, RoundingMode.HALF_UP).intValue();
    }

    private static BigDecimal sum(List<InvestmentDTO> items, java.util.function.Function<InvestmentDTO, BigDecimal> getter) {
        return items.stream().map(getter).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
