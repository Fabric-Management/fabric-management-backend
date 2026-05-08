package com.fabricmanagement.procurement.quote.app.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.procurement.quote.domain.SupplierQuote;
import com.fabricmanagement.procurement.quote.domain.SupplierQuoteStatus;
import com.fabricmanagement.procurement.quote.infra.repository.SupplierQuoteRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SupplierQuoteExpirationJobTest {

  @Mock private SupplierQuoteRepository quoteRepository;

  @InjectMocks private SupplierQuoteExpirationJob expirationJob;

  @Test
  void expireStaleQuotes_WithStaleQuotes_ExpiresThem() {
    SupplierQuote quote1 = new SupplierQuote();
    quote1.setValidUntil(LocalDate.now().minusDays(1));
    quote1.setStatus(SupplierQuoteStatus.RECEIVED);

    SupplierQuote quote2 = new SupplierQuote();
    quote2.setValidUntil(LocalDate.now().minusDays(2));
    quote2.setStatus(SupplierQuoteStatus.UNDER_REVIEW);

    List<SupplierQuote> staleQuotes = List.of(quote1, quote2);

    when(quoteRepository.findByValidUntilBeforeAndStatusInAndIsActiveTrue(
            any(LocalDate.class), any()))
        .thenReturn(staleQuotes);

    expirationJob.expireStaleQuotes();

    assertEquals(SupplierQuoteStatus.EXPIRED, quote1.getStatus());
    assertEquals(SupplierQuoteStatus.EXPIRED, quote2.getStatus());
    verify(quoteRepository).saveAll(staleQuotes);
  }

  @Test
  void expireStaleQuotes_NoStaleQuotes_DoesNothing() {
    when(quoteRepository.findByValidUntilBeforeAndStatusInAndIsActiveTrue(
            any(LocalDate.class), any()))
        .thenReturn(new ArrayList<>());

    expirationJob.expireStaleQuotes();

    verify(quoteRepository, never()).saveAll(any());
  }
}
