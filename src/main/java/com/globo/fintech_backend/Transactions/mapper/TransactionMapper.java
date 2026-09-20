package com.globo.fintech_backend.Transactions.mapper;
import com.globo.fintech_backend.Transactions.dto.TransactionDTO;
import com.globo.fintech_backend.Transactions.entity.Transaction;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TransactionMapper {

    TransactionDTO toDTO(Transaction transaction);
}