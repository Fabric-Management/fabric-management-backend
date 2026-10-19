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
import java.util.Objects;
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
  private final com.fabricmanagement.platform.realtime.app.LiveEditLeaseService leases;
  private final com.fabricmanagement.production.core.stockunit.infra.repository.StockUnitRepository
      stockUnits;

  /** Holds on the given lots of the current tenant; empty when no lot is given. */
  public QuoteHolds find(Collection<UUID> batchIds) {
    return find(batchIds, null, null);
  }

  public QuoteHolds find(Collection<UUID> batchIds, UUID ownSessionId, UUID ownLineId) {
    if (batchIds == null || batchIds.isEmpty()) {
      return new QuoteHolds(List.of(), List.of());
    }
    UUID tenantId = TenantContext.requireTenantId();
    Set<UUID> lotIds = Set.copyOf(batchIds);
    Map<UUID, Optional<PrimaryMeasure>> measures =
        batchRepository.findByTenantIdAndIdInAndIsActiveTrue(tenantId, lotIds).stream()
            .collect(Collectors.toMap(Batch::getId, this::measureOf));
    List<LotIntentView> intents =
        new java.util.ArrayList<>(
            intentRepository.findActiveByBatchIds(tenantId, lotIds, ownLineId).stream()
                .map(
                    intent ->
                        toView(
                            tenantId,
                            intent,
                            measures.getOrDefault(intent.getBatchId(), Optional.empty())))
                .toList());
    List<HeldPieceView> heldPieces =
        new java.util.ArrayList<>(
            softHoldRepository.findActiveByBatchIds(tenantId, lotIds).stream()
                .filter(row -> !java.util.Objects.equals(row.getQuoteLineId(), ownLineId))
                .map(
                    row ->
                        new HeldPieceView(
                            row.getStockUnitId(), row.getBatchId(), row.getQuoteLineId()))
                .toList());
    var lots = batchRepository.findByTenantIdAndIdInAndIsActiveTrue(tenantId, lotIds);
    var units =
        stockUnits.findByTenantIdAndBatchIdInAndIsActiveTrue(tenantId, List.copyOf(lotIds)).stream()
            .collect(
                Collectors.toMap(
                    com.fabricmanagement.production.core.stockunit.domain.StockUnit::getId,
                    java.util.function.Function.identity()));
    for (UUID productId :
        lots.stream().map(Batch::getProductId).filter(Objects::nonNull).distinct().toList()) {
      var bySession =
          leases
              .held(
                  com.fabricmanagement.production.core.stockunit.api.StockUnitLeaseKeys.resource(
                      productId),
                  1)
              .stream()
              .filter(lease -> !java.util.Objects.equals(lease.getSessionId(), ownSessionId))
              .filter(
                  lease ->
                      units.containsKey(
                          com.fabricmanagement.production.core.stockunit.api.StockUnitLeaseKeys
                              .pieceId(lease.key())))
              .collect(
                  Collectors.groupingBy(
                      com.fabricmanagement.platform.realtime.domain.LiveEditLease::getSessionId));
      bySession.forEach(
          (session, rows) -> {
            var byLot =
                rows.stream()
                    .collect(
                        Collectors.groupingBy(
                            lease ->
                                units
                                    .get(
                                        com.fabricmanagement.production.core.stockunit.api
                                            .StockUnitLeaseKeys.pieceId(lease.key()))
                                    .getBatchId()));
            byLot.forEach(
                (lotId, heldRows) -> {
                  List<BigDecimal> lengths =
                      heldRows.stream()
                          .map(
                              lease ->
                                  units.get(
                                      com.fabricmanagement.production.core.stockunit.api
                                          .StockUnitLeaseKeys.pieceId(lease.key())))
                          .map(
                              unit ->
                                  primaryMeasureService.toCanonical(
                                      unit.getLength(),
                                      unit.getLengthUnit(),
                                      PrimaryMeasure.LENGTH))
                          .flatMap(Optional::stream)
                          .toList();
                  BigDecimal total =
                      lengths.size() == heldRows.size()
                          ? lengths.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                          : null;
                  intents.add(
                      new LotIntentView(
                          lotId, null, session, null, null, total, "M", total, null, "DRAFT"));
                  heldRows.forEach(
                      lease ->
                          heldPieces.add(
                              new HeldPieceView(
                                  com.fabricmanagement.production.core.stockunit.api
                                      .StockUnitLeaseKeys.pieceId(lease.key()),
                                  lotId,
                                  session)));
                });
          });
    }
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
        intent.getOwnerLineId(),
        intent.getQuoteNumber(),
        intent.getMarketerName(),
        intent.getQuantity(),
        intent.getUnit(),
        canonical,
        intent.getExpiresAt(),
        intent.getOwnerType());
  }

  /** Active quote holds on a set of lots. */
  public record QuoteHolds(List<LotIntentView> intents, List<HeldPieceView> heldPieces) {

    public QuoteHolds {
      intents = List.copyOf(intents);
      heldPieces = List.copyOf(heldPieces);
    }

    /** A consumer may exempt only owners whose context it already authenticated. */
    public QuoteHolds excludingOwners(Set<UUID> owners) {
      if (owners.isEmpty()) return this;
      return new QuoteHolds(
          intents.stream().filter(intent -> !owners.contains(intent.quoteLineId())).toList(),
          heldPieces.stream().filter(piece -> !owners.contains(piece.quoteLineId())).toList());
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
      LocalDate expiresAt,
      String ownerType) {
    public LotIntentView(
        UUID batchId,
        UUID quoteId,
        UUID quoteLineId,
        String quoteNumber,
        String marketerName,
        BigDecimal quantity,
        String unit,
        BigDecimal canonicalQuantity,
        LocalDate expiresAt) {
      this(
          batchId,
          quoteId,
          quoteLineId,
          quoteNumber,
          marketerName,
          quantity,
          unit,
          canonicalQuantity,
          expiresAt,
          "QUOTE");
    }
  }

  /**
   * One active piece hold. It names only the quote line; whether a live quote intent backs it is
   * for the reader to establish (an intent of the same quote line on the same lot).
   */
  public record HeldPieceView(UUID stockUnitId, UUID batchId, UUID quoteLineId) {}
}
