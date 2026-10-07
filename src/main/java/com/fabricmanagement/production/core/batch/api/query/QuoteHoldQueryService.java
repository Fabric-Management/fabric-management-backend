package com.fabricmanagement.production.core.batch.api.query;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.core.batch.app.BatchPrimaryMeasureService;
import com.fabricmanagement.production.core.batch.domain.Batch;
import com.fabricmanagement.production.core.batch.domain.BatchLotQuantityIntent;
import com.fabricmanagement.production.core.batch.domain.PrimaryMeasure;
import com.fabricmanagement.production.core.batch.infra.repository.BatchLotQuantityIntentRepository;
import com.fabricmanagement.production.core.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.core.stockunit.infra.repository.StockUnitSoftHoldRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public production read contract for what quotes hold on a set of lots (STOCK-PREVIEW-1 A2): the
 * active lot-quantity intents and the active piece holds a quote line's piece selection placed.
 * Intent quantities are stated in the lot's canonical unit here, inside production, with the rule
 * {@link com.fabricmanagement.production.core.batch.app.BatchCommitmentQuantityService} applies for
 * the quote picker: a quantity that cannot be converted exactly is reported as {@code null} and is
 * never summed. Nothing is reserved, released or recorded.
 *
 * <p>Expired intents stay active until the nightly expiry job releases them; the quote picker reads
 * them the same way, so they are not filtered here.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class QuoteHoldQueryService {

  private final BatchRepository batchRepository;
  private final BatchLotQuantityIntentRepository intentRepository;
  private final StockUnitSoftHoldRepository softHoldRepository;
  private final BatchPrimaryMeasureService primaryMeasureService;

  /** Holds on the given lots of the current tenant; empty when no lot is given. */
  public QuoteHolds find(Collection<UUID> batchIds) {
    if (batchIds == null || batchIds.isEmpty()) {
      return new QuoteHolds(List.of(), List.of());
    }
    UUID tenantId = TenantContext.requireTenantId();
    Set<UUID> lotIds = Set.copyOf(batchIds);
    Map<UUID, Optional<PrimaryMeasure>> measures =
        batchRepository.findByTenantIdAndIdInAndIsActiveTrue(tenantId, lotIds).stream()
            .collect(Collectors.toMap(Batch::getId, this::measureOf));
    List<LotIntentView> intents =
        intentRepository.findActiveByBatchIds(tenantId, lotIds, null).stream()
            .map(
                intent ->
                    toView(
                        tenantId,
                        intent,
                        measures.getOrDefault(intent.getBatchId(), Optional.empty())))
            .toList();
    List<HeldPieceView> heldPieces =
        softHoldRepository.findActiveByBatchIds(tenantId, lotIds).stream()
            .map(
                row ->
                    new HeldPieceView(row.getStockUnitId(), row.getBatchId(), row.getQuoteLineId()))
            .toList();
    return new QuoteHolds(intents, heldPieces);
  }

  private Optional<PrimaryMeasure> measureOf(Batch batch) {
    return primaryMeasureService
        .findResolution(batch.getProductType())
        .map(BatchPrimaryMeasureService.Resolution::primaryMeasure);
  }

  private LotIntentView toView(
      UUID tenantId, BatchLotQuantityIntent intent, Optional<PrimaryMeasure> measure) {
    BigDecimal canonical =
        measure
            .flatMap(
                dimension ->
                    primaryMeasureService.toCanonical(
                        intent.getQuantity(), intent.getUnit(), dimension))
            .orElse(null);
    if (canonical == null) {
      log.warn(
          "Quote lot intent not convertible to the lot's canonical unit: tenantId={}, batchId={},"
              + " intentId={}, unit={}, quantity={}",
          tenantId,
          intent.getBatchId(),
          intent.getId(),
          primaryMeasureService.normalizeUnit(intent.getUnit()),
          intent.getQuantity());
    }
    return new LotIntentView(
        intent.getBatchId(),
        intent.getQuoteId(),
        intent.getQuoteLineId(),
        intent.getQuoteNumber(),
        intent.getMarketerName(),
        intent.getQuantity(),
        intent.getUnit(),
        canonical,
        intent.getExpiresAt());
  }

  /** Active quote holds on a set of lots. */
  public record QuoteHolds(List<LotIntentView> intents, List<HeldPieceView> heldPieces) {

    public QuoteHolds {
      intents = List.copyOf(intents);
      heldPieces = List.copyOf(heldPieces);
    }
  }

  /**
   * One active lot-quantity intent. {@code canonicalQuantity} is the quantity in the lot's
   * canonical unit, or null when it cannot be converted exactly.
   */
  public record LotIntentView(
      UUID batchId,
      UUID quoteId,
      UUID quoteLineId,
      String quoteNumber,
      String marketerName,
      BigDecimal quantity,
      String unit,
      BigDecimal canonicalQuantity,
      LocalDate expiresAt) {}

  /**
   * One active piece hold. It names only the quote line; whether a live quote intent backs it is
   * for the reader to establish (an intent of the same quote line on the same lot).
   */
  public record HeldPieceView(UUID stockUnitId, UUID batchId, UUID quoteLineId) {}
}
