package com.fabricmanagement.production.core.stockunit.app;

import com.fabricmanagement.production.core.batch.app.BatchPrimaryMeasureService;
import com.fabricmanagement.production.core.batch.domain.Batch;
import com.fabricmanagement.production.core.batch.domain.BatchReservation;
import com.fabricmanagement.production.core.batch.domain.PrimaryMeasure;
import com.fabricmanagement.production.core.batch.domain.ReservationStatus;
import com.fabricmanagement.production.core.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.core.batch.infra.repository.BatchReservationRepository;
import com.fabricmanagement.production.core.stockunit.api.PieceAllocationPort;
import com.fabricmanagement.production.core.stockunit.domain.QualityDisposition;
import com.fabricmanagement.production.core.stockunit.domain.StockUnit;
import com.fabricmanagement.production.core.stockunit.domain.StockUnitAllocation;
import com.fabricmanagement.production.core.stockunit.domain.StockUnitAllocationStatus;
import com.fabricmanagement.production.core.stockunit.domain.StockUnitCut;
import com.fabricmanagement.production.core.stockunit.domain.StockUnitStatus;
import com.fabricmanagement.production.core.stockunit.infra.repository.StockUnitAllocationRepository;
import com.fabricmanagement.production.core.stockunit.infra.repository.StockUnitCutRepository;
import com.fabricmanagement.production.core.stockunit.infra.repository.StockUnitRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Hard allocation of named pieces (SOI D4). Lots are locked in id order so two confirmations
 * touching the same lots serialise without deadlock; every piece is re-checked under the lock and
 * against the version the proposal saw. Validation happens before any change, so a failure leaves
 * nothing behind. The optimistic version on each piece is the last guard.
 */
@Service
@RequiredArgsConstructor
public class PieceAllocationService implements PieceAllocationPort {

  static final String REFERENCE_TYPE = "SALES_ORDER_LINE_PIECES";

  private final BatchRepository batchRepository;
  private final BatchReservationRepository reservationRepository;
  private final StockUnitRepository stockUnitRepository;
  private final StockUnitAllocationRepository allocationRepository;
  private final StockUnitCutRepository cutRepository;
  private final BatchPrimaryMeasureService measureService;
  private final Clock clock;

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void lockLots(UUID tenantId, Collection<UUID> batchIds) {
    Objects.requireNonNull(tenantId, "tenantId");
    for (UUID batchId : new TreeSet<>(batchIds)) {
      batchRepository.findByIdAndTenantIdForUpdate(batchId, tenantId);
    }
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public AllocationOutcome allocate(AllocationRequest request) {
    Objects.requireNonNull(request.tenantId(), "tenantId");
    if (request.pieces() == null || request.pieces().isEmpty()) {
      return AllocationOutcome.success(0);
    }
    Map<UUID, List<PieceRef>> byLot = new TreeMap<>();
    request
        .pieces()
        .forEach(ref -> byLot.computeIfAbsent(ref.batchId(), id -> new ArrayList<>()).add(ref));

    Map<UUID, Batch> lockedLots = new LinkedHashMap<>();
    for (UUID batchId : byLot.keySet()) {
      Optional<Batch> lot =
          batchRepository.findByIdAndTenantIdForUpdate(batchId, request.tenantId());
      if (lot.isEmpty()) {
        return AllocationOutcome.failure(
            Failures.PIECE_NOT_FOUND,
            byLot.get(batchId).stream().map(PieceRef::stockUnitId).toList());
      }
      lockedLots.put(batchId, lot.get());
    }

    Map<UUID, StockUnit> units =
        stockUnitRepository
            .findByTenantIdAndBatchIdInAndIsActiveTrue(
                request.tenantId(), new ArrayList<>(byLot.keySet()))
            .stream()
            .collect(Collectors.toMap(StockUnit::getId, Function.identity()));
    List<UUID> requestedIds = request.pieces().stream().map(PieceRef::stockUnitId).toList();
    List<UUID> alreadyHeld =
        allocationRepository
            .findByTenantIdAndStockUnitIdInAndStatus(
                request.tenantId(), requestedIds, StockUnitAllocationStatus.ACTIVE)
            .stream()
            .map(StockUnitAllocation::getStockUnitId)
            .toList();
    if (!alreadyHeld.isEmpty()) {
      return AllocationOutcome.failure(Failures.PIECE_TAKEN, alreadyHeld);
    }

    List<UUID> missing = new ArrayList<>();
    List<UUID> taken = new ArrayList<>();
    List<UUID> changed = new ArrayList<>();
    for (PieceRef ref : request.pieces()) {
      StockUnit unit = units.get(ref.stockUnitId());
      if (unit == null || !unit.getBatchId().equals(ref.batchId())) {
        missing.add(ref.stockUnitId());
      } else if (unit.getStatus() != StockUnitStatus.AVAILABLE
          || unit.getQualityDisposition() != QualityDisposition.RELEASED) {
        taken.add(ref.stockUnitId());
      } else if (ref.expectedVersion() != null
          && !ref.expectedVersion().equals(unit.getVersion())) {
        changed.add(ref.stockUnitId());
      }
    }
    if (!missing.isEmpty()) {
      return AllocationOutcome.failure(Failures.PIECE_NOT_FOUND, missing);
    }
    if (!taken.isEmpty()) {
      return AllocationOutcome.failure(Failures.PIECE_TAKEN, taken);
    }
    List<UUID> cutSinceProposal =
        cutRepository
            .findByTenantIdAndStockUnitIdInAndIsActiveTrue(request.tenantId(), requestedIds)
            .stream()
            .filter(cut -> !cut.isRemainingVerified() || cut.getRemainingLength().signum() == 0)
            .map(StockUnitCut::getStockUnitId)
            .distinct()
            .toList();
    changed.addAll(cutSinceProposal);
    if (!changed.isEmpty()) {
      return AllocationOutcome.failure(Failures.PIECE_CHANGED, changed);
    }

    Map<UUID, LotPlan> plans = new LinkedHashMap<>();
    for (Map.Entry<UUID, List<PieceRef>> entry : byLot.entrySet()) {
      Batch lot = lockedLots.get(entry.getKey());
      Optional<LotPlan> plan = plan(lot, entry.getValue(), units);
      if (plan.isEmpty()) {
        return AllocationOutcome.failure(
            Failures.MEASURE_UNRESOLVED,
            entry.getValue().stream().map(PieceRef::stockUnitId).toList());
      }
      if (lot.getAvailableQuantity().compareTo(plan.get().lotQuantity()) < 0) {
        return AllocationOutcome.failure(
            Failures.LOT_QUANTITY_INSUFFICIENT,
            entry.getValue().stream().map(PieceRef::stockUnitId).toList());
      }
      plans.put(lot.getId(), plan.get());
    }

    Instant now = clock.instant();
    int allocated = 0;
    for (Map.Entry<UUID, LotPlan> entry : plans.entrySet()) {
      Batch lot = lockedLots.get(entry.getKey());
      LotPlan plan = entry.getValue();
      lot.reserve(plan.lotQuantity());
      BatchReservation reservation =
          BatchReservation.create(
              request.tenantId(),
              lot.getId(),
              request.salesOrderLineId(),
              REFERENCE_TYPE,
              plan.canonicalTotal(),
              plan.canonicalUnit(),
              "Whole pieces held at sales-order confirmation");
      batchRepository.save(lot);
      reservation = reservationRepository.save(reservation);
      for (PieceShare share : plan.pieces()) {
        StockUnit unit = units.get(share.stockUnitId());
        unit.reserve();
        stockUnitRepository.save(unit);
        StockUnitAllocation allocation =
            StockUnitAllocation.allocate(
                unit.getId(),
                lot.getId(),
                reservation.getId(),
                request.salesOrderId(),
                request.salesOrderLineId(),
                share.canonicalQuantity(),
                plan.canonicalUnit(),
                request.actorId(),
                now);
        allocation.setTenantId(request.tenantId());
        allocationRepository.save(allocation);
        allocated++;
      }
    }
    return AllocationOutcome.success(allocated);
  }

  @Override
  @Transactional(readOnly = true)
  public List<PieceAllocationView> activeForLine(UUID tenantId, UUID salesOrderLineId) {
    return allocationRepository
        .findByTenantIdAndSalesOrderLineIdAndStatusOrderByAllocatedAtAscIdAsc(
            tenantId, salesOrderLineId, StockUnitAllocationStatus.ACTIVE)
        .stream()
        .map(PieceAllocationService::view)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<PieceAllocationView> activeForOrder(UUID tenantId, UUID salesOrderId) {
    return allocationRepository
        .findByTenantIdAndSalesOrderIdAndStatusOrderByAllocatedAtAscIdAsc(
            tenantId, salesOrderId, StockUnitAllocationStatus.ACTIVE)
        .stream()
        .map(PieceAllocationService::view)
        .toList();
  }

  @Override
  @Transactional
  public int releaseForLine(UUID tenantId, UUID salesOrderLineId, UUID actorId, String reason) {
    List<StockUnitAllocation> active =
        allocationRepository.findByTenantIdAndSalesOrderLineIdAndStatusOrderByAllocatedAtAscIdAsc(
            tenantId, salesOrderLineId, StockUnitAllocationStatus.ACTIVE);
    if (active.isEmpty()) {
      return 0;
    }
    Instant now = clock.instant();
    Map<UUID, List<StockUnitAllocation>> byReservation =
        active.stream()
            .collect(
                Collectors.groupingBy(
                    StockUnitAllocation::getBatchReservationId, TreeMap::new, Collectors.toList()));
    for (Map.Entry<UUID, List<StockUnitAllocation>> entry : byReservation.entrySet()) {
      reservationRepository
          .findByIdAndTenantId(entry.getKey(), tenantId)
          .filter(
              reservation ->
                  reservation.getStatus() == ReservationStatus.ACTIVE
                      || reservation.getStatus() == ReservationStatus.PARTIALLY_CONSUMED)
          .ifPresent(reservation -> cancelReservation(tenantId, reservation));
      for (StockUnitAllocation allocation : entry.getValue()) {
        stockUnitRepository
            .findByIdAndTenantIdAndIsActiveTrue(allocation.getStockUnitId(), tenantId)
            .filter(unit -> unit.getStatus() == StockUnitStatus.RESERVED)
            .ifPresent(
                unit -> {
                  unit.releaseReservation();
                  stockUnitRepository.save(unit);
                });
        allocation.release(actorId, now, reason);
        allocationRepository.save(allocation);
      }
    }
    return active.size();
  }

  private void cancelReservation(UUID tenantId, BatchReservation reservation) {
    BigDecimal remaining = reservation.cancel();
    reservationRepository.save(reservation);
    if (remaining.signum() <= 0) {
      return;
    }
    batchRepository
        .findByIdAndTenantIdForUpdate(reservation.getBatchId(), tenantId)
        .ifPresent(
            lot -> {
              PrimaryMeasure measure = measureService.resolve(lot).primaryMeasure();
              measureService
                  .fromCanonical(remaining, lot.getUnit(), measure)
                  .ifPresent(
                      quantity -> {
                        lot.release(quantity);
                        batchRepository.save(lot);
                      });
            });
  }

  private Optional<LotPlan> plan(Batch lot, Collection<PieceRef> refs, Map<UUID, StockUnit> units) {
    var resolution = measureService.findResolution(lot.getProductType());
    if (resolution.isEmpty()) {
      return Optional.empty();
    }
    PrimaryMeasure measure = resolution.get().primaryMeasure();
    List<PieceShare> shares = new ArrayList<>();
    BigDecimal total = BigDecimal.ZERO;
    for (PieceRef ref : refs) {
      StockUnit unit = units.get(ref.stockUnitId());
      Optional<BigDecimal> canonical =
          measure == PrimaryMeasure.LENGTH
              ? measureService.toCanonical(unit.getLength(), unit.getLengthUnit(), measure)
              : measureService.toCanonical(unit.getCurrentWeight(), unit.getUnit(), measure);
      if (canonical.isEmpty() || canonical.get().signum() <= 0) {
        return Optional.empty();
      }
      shares.add(new PieceShare(unit.getId(), canonical.get()));
      total = total.add(canonical.get());
    }
    Optional<BigDecimal> lotQuantity = measureService.fromCanonical(total, lot.getUnit(), measure);
    if (lotQuantity.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        new LotPlan(shares, total, resolution.get().primaryUnit(), lotQuantity.get()));
  }

  private static PieceAllocationView view(StockUnitAllocation allocation) {
    return new PieceAllocationView(
        allocation.getId(),
        allocation.getStockUnitId(),
        allocation.getBatchId(),
        allocation.getSalesOrderLineId(),
        allocation.getQuantity(),
        allocation.getUnit(),
        allocation.getAllocatedAt());
  }

  private record PieceShare(UUID stockUnitId, BigDecimal canonicalQuantity) {}

  private record LotPlan(
      List<PieceShare> pieces,
      BigDecimal canonicalTotal,
      String canonicalUnit,
      BigDecimal lotQuantity) {}
}
