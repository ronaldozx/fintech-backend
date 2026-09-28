package com.globo.fintech_backend.Transactions.service;

import com.globo.fintech_backend.Auth.entity.User;
import com.globo.fintech_backend.Auth.repository.UserRepository;
import com.globo.fintech_backend.Transactions.dto.TransactionCreateDTO;
import com.globo.fintech_backend.Transactions.dto.TransactionRowDTO;
import com.globo.fintech_backend.Transactions.dto.TransactionUpdateDTO;
import com.globo.fintech_backend.Transactions.entity.Transaction;
import com.globo.fintech_backend.Transactions.enums.PaymentMethod;
import com.globo.fintech_backend.Transactions.enums.TransactionType;
import com.globo.fintech_backend.Transactions.repository.TransactionRepository;
import com.globo.fintech_backend.exception.BadRequestException;
import com.globo.fintech_backend.exception.ConflictException;
import com.globo.fintech_backend.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Service
public class TransactionEditService {

    public static final String USER_REASON = "USER";

    static final int MAX_DESCRIPTION = 200;
    static final int MAX_CATEGORY = 100;
    private static final LocalDate EARLIEST_DATE = LocalDate.of(2000, 1, 1);

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    public TransactionEditService(TransactionRepository transactionRepository, UserRepository userRepository) {
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public TransactionRowDTO create(Long userId, TransactionCreateDTO request, LocalDate today) {
        if (request.type() == null) {
            throw new BadRequestException("Informe se é receita ou despesa");
        }

        String description = validDescription(request.description());
        BigDecimal amount = signed(request.type(), validAmount(request.amount()));
        LocalDate date = validDate(request.date(), today);
        String category = optionalCategory(request.category());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado"));

        Transaction transaction = new Transaction();
        transaction.setUser(user);
        transaction.setDescription(description);
        transaction.setType(request.type());
        transaction.setAmount(amount);
        transaction.setDate(date);
        transaction.setCategory(category);
        transaction.setPaymentMethod(PaymentMethod.DEBIT);
        transaction.setNeutral(false);
        transaction.setManual(true);
        transaction.setUserEdited(true);

        return TransactionSearchService.toRow(transactionRepository.save(transaction));
    }

    @Transactional
    public TransactionRowDTO update(Long userId, Long id, TransactionUpdateDTO request, LocalDate today) {
        Transaction transaction = find(userId, id);
        boolean manual = Boolean.TRUE.equals(transaction.getManual());

        if (!manual && request.touchesManualOnlyFields()) {
            throw new BadRequestException("Lançamentos importados do banco só permitem alterar a categoria e se contam nos totais");
        }

        if (request.category() != null) {
            transaction.setCategory(requiredCategory(request.category()));
        }

        if (request.neutral() != null) {
            transaction.setNeutral(request.neutral());
            transaction.setNeutralReason(request.neutral() ? USER_REASON : null);
        }

        if (manual) {
            applyManualFields(transaction, request, today);
        }

        transaction.setUserEdited(true);
        return TransactionSearchService.toRow(transactionRepository.save(transaction));
    }

    @Transactional
    public void delete(Long userId, Long id) {
        Transaction transaction = find(userId, id);

        if (!Boolean.TRUE.equals(transaction.getManual())) {
            throw new ConflictException("Lançamentos importados do banco não podem ser excluídos; marque como fora dos totais");
        }

        transactionRepository.delete(transaction);
    }

    private void applyManualFields(Transaction transaction, TransactionUpdateDTO request, LocalDate today) {
        if (request.description() != null) {
            transaction.setDescription(validDescription(request.description()));
        }
        if (request.date() != null) {
            transaction.setDate(validDate(request.date(), today));
        }
        if (request.type() != null || request.amount() != null) {
            TransactionType type = request.type() != null ? request.type() : transaction.getType();
            BigDecimal amount = request.amount() != null ? validAmount(request.amount()) : transaction.getAmount().abs();
            transaction.setType(type);
            transaction.setAmount(signed(type, amount));
        }
    }

    private Transaction find(Long userId, Long id) {
        return transactionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Lançamento não encontrado"));
    }

    private static BigDecimal signed(TransactionType type, BigDecimal amount) {
        return type == TransactionType.EXPENSE ? amount.negate() : amount;
    }

    private static BigDecimal validAmount(BigDecimal amount) {
        if (amount == null || amount.abs().signum() == 0) {
            throw new BadRequestException("O valor deve ser maior que zero");
        }
        return amount.abs().setScale(2, RoundingMode.HALF_UP);
    }

    private static String validDescription(String description) {
        if (description == null || description.isBlank()) {
            throw new BadRequestException("Informe uma descrição");
        }
        String trimmed = description.trim();
        if (trimmed.length() > MAX_DESCRIPTION) {
            throw new BadRequestException("A descrição pode ter no máximo " + MAX_DESCRIPTION + " caracteres");
        }
        return trimmed;
    }

    private static LocalDate validDate(LocalDate date, LocalDate today) {
        if (date == null) {
            throw new BadRequestException("Informe a data");
        }
        if (date.isBefore(EARLIEST_DATE) || date.isAfter(today.plusYears(1))) {
            throw new BadRequestException("Data fora do intervalo aceito");
        }
        return date;
    }

    private static String optionalCategory(String category) {
        return category == null || category.isBlank() ? null : requiredCategory(category);
    }

    private static String requiredCategory(String category) {
        String trimmed = category.trim();
        if (trimmed.isEmpty()) {
            throw new BadRequestException("Informe a categoria");
        }
        if (trimmed.length() > MAX_CATEGORY) {
            throw new BadRequestException("A categoria pode ter no máximo " + MAX_CATEGORY + " caracteres");
        }
        return trimmed;
    }
}
