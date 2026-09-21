package com.globo.fintech_backend.OpenFinance.connection;

import com.globo.fintech_backend.Auth.entity.User;
import com.globo.fintech_backend.Auth.repository.UserRepository;
import com.globo.fintech_backend.OpenFinance.connection.dto.BankConnectionDTO;
import com.globo.fintech_backend.OpenFinance.connection.dto.ConnectTokenDTO;
import com.globo.fintech_backend.OpenFinance.provider.OpenFinanceProvider;
import com.globo.fintech_backend.OpenFinance.provider.ProviderItem;
import com.globo.fintech_backend.exception.BadRequestException;
import com.globo.fintech_backend.exception.ConflictException;
import com.globo.fintech_backend.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BankConnectionService {

    private final BankConnectionRepository repository;
    private final UserRepository userRepository;
    private final OpenFinanceProvider provider;

    public BankConnectionService(BankConnectionRepository repository,
                                 UserRepository userRepository,
                                 OpenFinanceProvider provider) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.provider = provider;
    }

    public ConnectTokenDTO createConnectToken(Long userId) {
        return new ConnectTokenDTO(provider.createConnectToken(userId));
    }

    public BankConnectionDTO register(Long userId, String itemId) {
        if (itemId == null || itemId.isBlank()) {
            throw new BadRequestException("itemId é obrigatório");
        }

        BankConnection existing = repository.findByItemId(itemId).orElse(null);
        if (existing != null) {
            if (existing.getUser().getId().equals(userId)) {
                return toDTO(existing);
            }
            throw new ConflictException("Conexão já vinculada a outro usuário");
        }

        ProviderItem item = provider.getItem(itemId);
        if (!String.valueOf(userId).equals(item.clientUserId())) {
            throw new ResourceNotFoundException("Conexão não encontrada");
        }

        String institutionName = item.institutionName();
        if (institutionName != null && repository.existsByUserIdAndInstitutionName(userId, institutionName)) {
            throw new ConflictException("Você já tem uma conexão com " + institutionName
                    + ". Desconecte a atual antes de conectar de novo, senão as transações entram em dobro.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        BankConnection connection = new BankConnection();
        connection.setUser(user);
        connection.setItemId(item.id());
        connection.setInstitutionName(item.institutionName());
        connection.setStatus(item.status());

        return toDTO(repository.save(connection));
    }

    public List<BankConnectionDTO> list(Long userId) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toDTO)
                .toList();
    }

    public void delete(Long userId, Long connectionId) {
        BankConnection connection = repository.findByIdAndUserId(connectionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Conexão não encontrada"));

        provider.deleteItem(connection.getItemId());
        repository.delete(connection);
    }

    private BankConnectionDTO toDTO(BankConnection connection) {
        return new BankConnectionDTO(
                connection.getId(),
                connection.getItemId(),
                connection.getInstitutionName(),
                connection.getStatus(),
                connection.getCreatedAt()
        );
    }
}
