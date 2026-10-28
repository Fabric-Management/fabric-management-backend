package com.fabricmanagement.production.core.stockunit.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.realtime.app.LiveEditLeaseService;
import com.fabricmanagement.production.core.batch.domain.ReservationStatus;
import com.fabricmanagement.production.core.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.core.batch.infra.repository.BatchReservationRepository;
import com.fabricmanagement.production.core.stockunit.api.StockUnitLeaseKeys;
import com.fabricmanagement.production.core.stockunit.api.StockUnitOwnershipPort;
import com.fabricmanagement.production.core.stockunit.domain.StockUnit;
import com.fabricmanagement.production.core.stockunit.domain.StockUnitAllocationStatus;
import com.fabricmanagement.production.core.stockunit.domain.StockUnitStatus;
import com.fabricmanagement.production.core.stockunit.domain.exception.StockUnitHeldException;
import com.fabricmanagement.production.core.stockunit.infra.repository.StockUnitAllocationRepository;
import com.fabricmanagement.production.core.stockunit.infra.repository.StockUnitRepository;
import com.fabricmanagement.production.core.stockunit.infra.repository.StockUnitSoftHoldRepository;
import jakarta.persistence.EntityManager;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Product lease-control lock, then lot locks, then piece ownership checks for every writer. */
@Service
@RequiredArgsConstructor
public class StockUnitOwnershipService implements StockUnitOwnershipPort {
  private final LiveEditLeaseService leases;
  private final BatchRepository batches;
  private final StockUnitRepository pieces;
  private final StockUnitSoftHoldRepository holds;
  private final StockUnitAllocationRepository allocations;
  private final BatchReservationRepository reservations;
  private final EntityManager entityManager;

  @Override
  @Transactional(readOnly = true)
  public String availabilityRevision(UUID productId) {
    return pieces.availabilityRevision(TenantContext.requireTenantId(), productId);
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void lockProduct(UUID productId) {
    // A whole order may touch several products. One short tenant writer gate prevents reversed
    // product acquisition orders from deadlocking; no lock survives this DB transaction.
    leases.lock(
        new com.fabricmanagement.platform.realtime.domain.LiveResource(
            "stock-inventory", TenantContext.requireTenantId()));
    leases.lock(StockUnitLeaseKeys.resource(productId));
  }

  @Override
  @Transactional(readOnly = true)
  public List<UUID> productIdsForLots(Collection<UUID> batchIds) {
    if (batchIds.isEmpty()) return List.of();
    return batches
        .findByTenantIdAndIdInAndIsActiveTrue(TenantContext.requireTenantId(), batchIds)
        .stream()
        .map(com.fabricmanagement.production.core.batch.domain.Batch::getProductId)
        .distinct()
        .sorted()
        .toList();
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void lockLots(Collection<UUID> batchIds) {
    productIdsForLots(batchIds).forEach(this::lockProduct);
    batchIds.stream()
        .distinct()
        .sorted()
        .forEach(id -> batches.findByIdAndTenantIdForUpdate(id, TenantContext.requireTenantId()));
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void assertAnonymousLotsFree(Collection<UUID> batchIds) {
    lockLots(batchIds);
    var ids =
        pieces
            .findByTenantIdAndBatchIdInAndIsActiveTrue(
                TenantContext.requireTenantId(), List.copyOf(batchIds))
            .stream()
            .map(com.fabricmanagement.production.core.stockunit.domain.StockUnit::getId)
            .toList();
    assertAvailable(ids, null, null);
  }

  @Override
  @Transactional(readOnly = true)
  public Set<UUID> provisionallyHeld(UUID productId, UUID ownSessionId) {
    return leases
        .held(StockUnitLeaseKeys.resource(productId), StockUnitLeaseKeys.GENERATION)
        .stream()
        .filter(lease -> !Objects.equals(lease.getSessionId(), ownSessionId))
        .map(lease -> StockUnitLeaseKeys.pieceId(lease.key()))
        .collect(Collectors.toSet());
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void assertAvailable(Collection<UUID> pieceIds, UUID ownLineId, UUID ownSessionId) {
    assertOwnership(pieceIds, ownLineId, ownSessionId, false);
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void assertReservedConsumption(UUID pieceId, UUID workOrderId) {
    var units = assertOwnership(List.of(pieceId), null, null, true);
    if (workOrderId == null) throw new StockUnitHeldException();
    var unit = units.getFirst();
    var reservation =
        reservations
            .findFirstByTenantIdAndBatchIdAndReferenceIdAndStatusInAndIsActiveTrue(
                TenantContext.requireTenantId(),
                unit.getBatchId(),
                workOrderId,
                Set.of(ReservationStatus.ACTIVE, ReservationStatus.PARTIALLY_CONSUMED))
            .filter(row -> "WORK_ORDER".equals(row.getReferenceType()))
            .filter(row -> row.getRemainingQuantity().signum() > 0);
    if (reservation.isEmpty()) throw new StockUnitHeldException();
  }

  private List<StockUnit> assertOwnership(
      Collection<UUID> pieceIds, UUID ownLineId, UUID ownSessionId, boolean reservedConsumption) {
    if (pieceIds.isEmpty()) return List.of();
    UUID tenant = TenantContext.requireTenantId();
    var units = pieces.findByTenantIdAndIdInAndIsActiveTrue(tenant, pieceIds);
    if (units.size() != Set.copyOf(pieceIds).size()) throw new StockUnitHeldException();
    Set<UUID> lots =
        units.stream()
            .map(com.fabricmanagement.production.core.stockunit.domain.StockUnit::getBatchId)
            .collect(Collectors.toSet());
    lockLots(lots);
    units.forEach(entityManager::refresh);
    boolean leased =
        productIdsForLots(lots).stream()
            .anyMatch(
                product ->
                    provisionallyHeld(product, ownSessionId).stream().anyMatch(pieceIds::contains));
    boolean held =
        holds.findActiveByStockUnitIds(tenant, pieceIds).stream()
            .anyMatch(hold -> !Objects.equals(hold.getOwnerLineId(), ownLineId));
    var hard =
        allocations.findByTenantIdAndStockUnitIdInAndStatus(
            tenant, pieceIds, StockUnitAllocationStatus.ACTIVE);
    boolean allocated =
        hard.stream().anyMatch(row -> !Objects.equals(row.getSalesOrderLineId(), ownLineId));
    boolean physicallyUnavailable =
        units.stream()
            .anyMatch(
                unit ->
                    unit.getStatus() != StockUnitStatus.AVAILABLE
                        && unit.getStatus() != StockUnitStatus.PARTIAL
                        && !(reservedConsumption && unit.getStatus() == StockUnitStatus.RESERVED)
                        && hard.stream()
                            .noneMatch(
                                row ->
                                    row.getStockUnitId().equals(unit.getId())
                                        && Objects.equals(row.getSalesOrderLineId(), ownLineId)));
    if (leased || held || allocated || physicallyUnavailable) throw new StockUnitHeldException();
    return units;
  }
}
