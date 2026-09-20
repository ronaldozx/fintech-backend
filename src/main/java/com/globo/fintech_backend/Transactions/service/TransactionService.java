package com.globo.fintech_backend.Transactions.service;

import com.globo.fintech_backend.Auth.entity.User;
import com.globo.fintech_backend.Auth.repository.UserRepository;
import com.globo.fintech_backend.Transactions.dto.TransactionDTO;
import com.globo.fintech_backend.Transactions.dto.TransactionDashboardDTO;
import com.globo.fintech_backend.Transactions.entity.Transaction;
import com.globo.fintech_backend.Transactions.mapper.TransactionMapper;
import com.globo.fintech_backend.Transactions.repository.TransactionRepository;
import com.globo.fintech_backend.Transactions.repository.TransactionSummary;
import com.globo.fintech_backend.Transactions.service.parser.OfxParser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final OfxParser ofxParser;
    private final TransactionMapper transactionMapper;

    public TransactionService(TransactionRepository transactionRepository, UserRepository userRepository, OfxParser ofxParser, TransactionMapper transactionMapper){
        this.ofxParser = ofxParser;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.transactionMapper = transactionMapper;
    }

    public void importOfx(MultipartFile file, Long userId) throws Exception {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

        List<TransactionDTO> dtos = ofxParser.parse(file);

        for (TransactionDTO dto : dtos) {
            Transaction transaction = new Transaction();
            transaction.setUser(user);
            transaction.setDescription(dto.description());
            transaction.setAmount(dto.amount());
            transaction.setDate(dto.date());
            transaction.setPaymentMethod(dto.paymentMethod());
            transaction.setType(dto.type());
            transaction.setCategory(dto.category());
            transactionRepository.save(transaction);
        }
    }

    public TransactionDashboardDTO getDashboardData(Long userId, LocalDate startDate, LocalDate endDate, Pageable pageable){
        Page<TransactionDTO> page = transactionRepository.findByUserIdAndDateBetween(userId, startDate, endDate, pageable);
        TransactionSummary summary = transactionRepository.getSummary(userId, startDate, endDate);
        return new TransactionDashboardDTO(summary, page);
    }
}