package com.fabricmanagement.procurement.quote.app.scheduler;

import com.fabricmanagement.procurement.quote.domain.SupplierQuote;
import com.fabricmanagement.procurement.quote.domain.SupplierQuoteStatus;
import com.fabricmanagement.procurement.quote.infra.repository.SupplierQuoteRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Background job that automatically marks SupplierQuotes as EXPIRED if their validUntil date has
 * passed. Processes all tenants collectively.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SupplierQuoteExpirationJob {

  private final SupplierQuoteRepository quoteRepository;

  @Scheduled(cron = "${procurement.quote.expiration.cron:0 0 1 * * ?}")
  @Transactional
  public void expireStaleQuotes() {
    log.info("Starting SupplierQuoteExpirationJob to find and expire stale quotes...");
    LocalDate today = LocalDate.now();

    List<SupplierQuote> staleQuotes =
        quoteRepository.findByValidUntilBeforeAndStatusInAndIsActiveTrue(
            today, List.of(SupplierQuoteStatus.RECEIVED, SupplierQuoteStatus.UNDER_REVIEW));

    if (!staleQuotes.isEmpty()) {
      staleQuotes.forEach(
          quote -> {
            quote.expire();
            log.info(
                "Quote {} expired automatically (validUntil={})",
                quote.getQuoteNumber(),
                quote.getValidUntil());
          });

      quoteRepository.saveAll(staleQuotes);
      log.warn("SupplierQuoteExpirationJob expired {} stale quotes.", staleQuotes.size());
    } else {
      log.debug("No stale supplier quotes found.");
    }
  }
}
