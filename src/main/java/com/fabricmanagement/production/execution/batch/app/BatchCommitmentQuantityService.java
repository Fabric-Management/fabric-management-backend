package com.fabricmanagement.production.execution.batch.app;

import com.fabricmanagement.production.execution.batch.domain.Batch;
import com.fabricmanagement.production.execution.batch.domain.BatchLotQuantityIntentStatus;
import com.fabricmanagement.production.execution.batch.domain.BatchUnitMismatchSource;
import com.fabricmanagement.production.execution.batch.domain.ReservationStatus;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchLotQuantityIntentRepository;
import com.fabricmanagement.production.execution.batch.infra.repository.BatchReservationRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Unit-aware bulk aggregation of batch-grain intents and reservations. */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class BatchCommitmentQuantityService {

  private final BatchLotQuantityIntentRepository intentRepository;
  private final BatchReservationRepository reservationRepository;
  private final BatchPrimaryMeasureService primaryMeasureService;

  public Map<UUID, Summary> summarize(
      UUID tenantId, Collection<Batch> batches, UUID excludedQuoteLineId) {
    if (batches == null || batches.isEmpty()) {
      return Map.of();
    }
    Map<UUID, Batch> batchesById =
        batches.stream().collect(Collectors.toMap(Batch::getId, Function.identity()));
    List<UUID> batchIds = List.copyOf(batchesById.keySet());
    Map<UUID, MutableSummary> summaries = new HashMap<>();

    intentRepository
        .sumActiveRowsByBatchIdsAndUnit(
            tenantId, batchIds, BatchLotQuantityIntentStatus.ACTIVE, excludedQuoteLineId)
        .forEach(
            row ->
                add(
                    tenantId,
                    batchesById.get(row.getBatchId()),
                    summaries,
                    BatchUnitMismatchSource.SOFT_INTENT,
                    row.getUnit(),
                    row.getQuantity(),
                    row.getRowCount()));

    reservationRepository
        .sumRemainingRowsByBatchIdsAndUnit(
            tenantId,
            batchIds,
            List.of(ReservationStatus.ACTIVE, ReservationStatus.PARTIALLY_CONSUMED))
        .forEach(
            row ->
                add(
                    tenantId,
                    batchesById.get(row.getBatchId()),
                    summaries,
                    BatchUnitMismatchSource.HARD_RESERVATION,
                    row.getUnit(),
                    row.getQuantity(),
                    row.getRowCount()));

    return batchesById.keySet().stream()
        .collect(
            Collectors.toMap(
                Function.identity(),
                batchId -> summaries.getOrDefault(batchId, new MutableSummary()).freeze()));
  }

  private void add(
      UUID tenantId,
      Batch batch,
      Map<UUID, MutableSummary> summaries,
      BatchUnitMismatchSource source,
      String unit,
      BigDecimal quantity,
      long rowCount) {
    if (batch == null) {
      return;
    }
    MutableSummary summary =
        summaries.computeIfAbsent(batch.getId(), ignored -> new MutableSummary());
    var measure = primaryMeasureService.resolve(batch).primaryMeasure();
    primaryMeasureService
        .toCanonical(quantity, unit, measure)
        .ifPresentOrElse(
            canonical -> summary.add(source, canonical),
            () -> {
              String normalized = primaryMeasureService.normalizeUnit(unit);
              UnitMismatch mismatch = new UnitMismatch(source, normalized, quantity, rowCount);
              summary.mismatches.add(mismatch);
              log.warn(
                  "Excluded non-canonical batch commitment: tenantId={}, batchId={}, source={}, unit={}, quantity={}, rowCount={}",
                  tenantId,
                  batch.getId(),
                  source,
                  normalized,
                  quantity,
                  rowCount);
            });
  }

  public record UnitMismatch(
      BatchUnitMismatchSource source, String unit, BigDecimal quantity, long rowCount) {}

  public record Summary(
      BigDecimal softIntent, BigDecimal hardReserved, List<UnitMismatch> unitMismatches) {}

  private static final class MutableSummary {
    private BigDecimal softIntent = BigDecimal.ZERO;
    private BigDecimal hardReserved = BigDecimal.ZERO;
    private final List<UnitMismatch> mismatches = new ArrayList<>();

    private void add(BatchUnitMismatchSource source, BigDecimal quantity) {
      if (source == BatchUnitMismatchSource.SOFT_INTENT) {
        softIntent = softIntent.add(quantity);
      } else {
        hardReserved = hardReserved.add(quantity);
      }
    }

    private Summary freeze() {
      return new Summary(softIntent, hardReserved, List.copyOf(mismatches));
    }
  }
}
