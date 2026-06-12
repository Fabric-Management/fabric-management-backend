package com.fabricmanagement.finance.common.app;

import com.fabricmanagement.finance.common.domain.DocumentNumberCounter;
import com.fabricmanagement.finance.common.infra.DocumentNumberCounterRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class FinanceDocumentNumberGenerator {

  private final DocumentNumberCounterRepository counterRepository;

  /**
   * Generates the next document number within the current transaction. Format:
   * {SERIES}-{YYYY}-{NNNNNN} → SF-2026-000123
   *
   * <p><b>Gapless by construction:</b> Uses SELECT FOR UPDATE + increment. If the transaction rolls
   * back, the counter is never committed.
   *
   * <p><b>Trade-off:</b> Counter row serializes per (tenant, series, year). Acceptable for invoice
   * volume; if hot-spot appears, shard per-tenant.
   *
   * <p><b>Lock ordering:</b> If a future flow allocates numbers from multiple series in one
   * transaction, acquire counters in a consistent series order (alphabetical) to avoid lock-order
   * deadlock.
   */
  @Transactional(propagation = Propagation.MANDATORY) // Must run inside caller's tx
  public String nextNumber(UUID tenantId, String series, int year) {
    counterRepository.ensureCounterExists(tenantId, series, year);
    DocumentNumberCounter counter =
        counterRepository
            .findForUpdate(tenantId, series, year)
            .orElseThrow(() -> new IllegalStateException("Counter row missing after insert"));

    counter.setLastValue(counter.getLastValue() + 1);
    counterRepository.save(counter);

    return String.format("%s-%d-%06d", series, year, counter.getLastValue());
  }
}
