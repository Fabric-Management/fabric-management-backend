package com.fabricmanagement.production.core.workorder.api.query;

import com.fabricmanagement.production.core.workorder.infra.repository.WorkOrderHistoryRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * History of completed work orders of a product (SOI A08, R13, R15). These are estimates with their
 * sample size and basis time, never a commitment: planning or the dyehouse confirms. No value is
 * assumed when there is no history.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductionHistoryQueryService {

  /** How many recent completions a lead-time range is taken from. */
  static final int LEAD_TIME_SAMPLE = 20;

  private final WorkOrderHistoryRepository repository;

  public YieldHistory yieldHistory(UUID tenantId, UUID productId) {
    WorkOrderHistoryRepository.YieldRow row = repository.yieldForProduct(tenantId, productId);
    long size = row == null || row.getSampleSize() == null ? 0 : row.getSampleSize();
    if (size == 0) {
      return new YieldHistory(0, null, null, null, null);
    }
    return new YieldHistory(
        size,
        BigDecimal.valueOf(row.getAvgYield()).setScale(2, RoundingMode.HALF_UP),
        row.getMinYield(),
        row.getMaxYield(),
        row.getLastCompletedAt());
  }

  public LeadTimeHistory leadTimeHistory(UUID tenantId, UUID productId) {
    List<WorkOrderHistoryRepository.LeadRow> rows =
        repository.recentCompleted(tenantId, productId, PageRequest.of(0, LEAD_TIME_SAMPLE));
    List<Long> days =
        rows.stream()
            .filter(row -> row.getCreatedAt() != null && row.getCompletedAt() != null)
            .map(row -> Duration.between(row.getCreatedAt(), row.getCompletedAt()).toDays())
            .filter(value -> value >= 0)
            .sorted()
            .toList();
    if (days.isEmpty()) {
      return new LeadTimeHistory(0, null, null, null, null);
    }
    Instant basis = rows.getFirst().getCompletedAt();
    return new LeadTimeHistory(
        days.size(), days.getFirst(), days.get(days.size() / 2), days.getLast(), basis);
  }

  /** Completed yield of the product's work orders; percentages as recorded at completion. */
  public record YieldHistory(
      long sampleSize,
      BigDecimal averagePercent,
      BigDecimal minPercent,
      BigDecimal maxPercent,
      Instant lastCompletedAt) {}

  /** Days from work-order creation to completion over recent completions. */
  public record LeadTimeHistory(
      long sampleSize, Long minDays, Long medianDays, Long maxDays, Instant basisAt) {}
}
