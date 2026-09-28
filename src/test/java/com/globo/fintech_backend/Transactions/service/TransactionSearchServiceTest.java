package com.globo.fintech_backend.Transactions.service;

import com.globo.fintech_backend.Transactions.export.TransactionCsvWriter;
import com.globo.fintech_backend.Transactions.query.TransactionFilter;
import com.globo.fintech_backend.Transactions.repository.TransactionRepository;
import com.globo.fintech_backend.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class TransactionSearchServiceTest {

    private TransactionSearchService service;

    @BeforeEach
    void setUp() {
        service = new TransactionSearchService(mock(TransactionRepository.class), new TransactionCsvWriter());
    }

    private static TransactionFilter filter(LocalDate start, LocalDate end) {
        return new TransactionFilter(1L, start, end, null, null, null, null, null);
    }

    @Test
    void defaultsToNewestFirst() {
        Sort sort = service.sanitize(Sort.unsorted());

        assertEquals(List.of("date", "id"), sort.stream().map(Sort.Order::getProperty).toList());
        assertEquals(Sort.Direction.DESC, sort.getOrderFor("date").getDirection());
    }

    @Test
    void keepsAllowedColumnsAndAddsTheIdAsTieBreaker() {
        Sort sort = service.sanitize(Sort.by(Sort.Order.asc("amount")));

        assertEquals(Sort.Direction.ASC, sort.getOrderFor("amount").getDirection());
        assertEquals(Sort.Direction.DESC, sort.getOrderFor("id").getDirection());
    }

    @Test
    void rejectsSortingByAColumnThatIsNotExposed() {
        assertThrows(BadRequestException.class, () -> service.sanitize(Sort.by("user.password")));
        assertThrows(BadRequestException.class, () -> service.sanitize(Sort.by("externalId")));
    }

    @Test
    void rejectsAnInvertedDateRangeOnSearchAndExport() {
        LocalDate later = LocalDate.of(2026, 12, 31);
        LocalDate earlier = LocalDate.of(2026, 1, 1);

        assertThrows(BadRequestException.class, () -> service.search(filter(later, earlier), PageRequest.of(0, 10)));
        assertThrows(BadRequestException.class, () -> service.exportCsv(filter(later, earlier), Sort.unsorted()));
    }
}
