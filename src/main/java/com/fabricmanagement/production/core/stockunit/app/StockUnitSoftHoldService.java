package com.fabricmanagement.production.core.stockunit.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.core.stockunit.api.StockUnitSoftHoldPort;
import com.fabricmanagement.production.core.stockunit.domain.StockUnitSoftHold;
import com.fabricmanagement.production.core.stockunit.domain.StockUnitSoftHoldStatus;
import com.fabricmanagement.production.core.stockunit.domain.event.StockUnitSoftHoldPlacedEvent;
import com.fabricmanagement.production.core.stockunit.domain.event.StockUnitSoftHoldReleasedEvent;
import com.fabricmanagement.production.core.stockunit.infra.repository.StockUnitSoftHoldRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StockUnitSoftHoldService implements StockUnitSoftHoldPort {

  private final StockUnitSoftHoldRepository repository;
  private final com.fabricmanagement.production.core.stockunit.infra.repository.StockUnitRepository
      pieces;
  private final ApplicationEventPublisher eventPublisher;
  private final com.fabricmanagement.production.core.stockunit.api.StockUnitOwnershipPort ownership;
  private final com.fabricmanagement.production.core.batch.infra.repository
          .BatchLotQuantityIntentRepository
      intents;

  @Override
  @Transactional
  public void replaceHolds(UUID quoteLineId, Collection<UUID> stockUnitIds) {
    UUID tenantId = TenantContext.requireTenantId();
    Set<UUID> requested =
        stockUnitIds == null ? Set.of() : stockUnitIds.stream().collect(Collectors.toSet());
    ownership.assertAvailable(requested, quoteLineId, null);
    repository
        .findByTenantIdAndQuoteLineId(tenantId, quoteLineId)
        .forEach(
            existing -> {
              if (!requested.contains(existing.getStockUnitId())
                  && existing.getStatus() == StockUnitSoftHoldStatus.ACTIVE
                  && existing.release(Instant.now())) {
                repository.save(existing);
                if ("QUOTE".equals(existing.getOwnerType()))
                  eventPublisher.publishEvent(
                      new StockUnitSoftHoldReleasedEvent(
                          tenantId, quoteLineId, existing.getStockUnitId(), existing.getId()));
              }
            });
    requested.forEach(stockUnitId -> placeOrReactivate(tenantId, quoteLineId, stockUnitId));
  }

  @Override
  @Transactional
  public void releaseHolds(UUID quoteLineId) {
    UUID tenantId = TenantContext.requireTenantId();
    intents
        .findByTenantIdAndOwnerLineId(tenantId, quoteLineId)
        .forEach(intent -> intent.release(Instant.now()));
    repository.findByTenantIdAndOwnerLineId(tenantId, quoteLineId).stream()
        .filter(existing -> existing.getStatus() == StockUnitSoftHoldStatus.ACTIVE)
        .forEach(
            existing -> {
              if (existing.release(Instant.now())) {
                repository.save(existing);
                if ("QUOTE".equals(existing.getOwnerType()))
                  eventPublisher.publishEvent(
                      new StockUnitSoftHoldReleasedEvent(
                          tenantId, quoteLineId, existing.getStockUnitId(), existing.getId()));
              }
            });
  }

  @Override
  @Transactional
  public void replaceOrderHolds(
      UUID orderId,
      String orderNumber,
      UUID lineId,
      UUID actor,
      UUID ownSessionId,
      Collection<UUID> pieceIds,
      Collection<OrderLot> lots) {
    UUID tenant = TenantContext.requireTenantId();
    ownership.assertAvailable(pieceIds, lineId, ownSessionId);
    var old = repository.findByTenantIdAndOwnerLineId(tenant, lineId);
    old.stream()
        .filter(hold -> !pieceIds.contains(hold.getStockUnitId()))
        .forEach(hold -> hold.release(Instant.now()));
    for (UUID id : pieceIds) {
      var row = old.stream().filter(hold -> hold.getStockUnitId().equals(id)).findFirst();
      if (row.isPresent()) row.get().reactivate();
      else
        repository.save(
            com.fabricmanagement.production.core.stockunit.domain.StockUnitSoftHold.forOrder(
                tenant, orderId, lineId, id));
    }
    // One existing ledger: order intents use explicit owner fields, never quote identifiers.
    intents
        .findByTenantIdAndOwnerLineId(tenant, lineId)
        .forEach(intent -> intent.release(Instant.now()));
    intents.flush();
    for (OrderLot lot : lots) {
      var oldIntent =
          intents.findByTenantIdAndOwnerLineId(tenant, lineId).stream()
              .filter(value -> value.getBatchId().equals(lot.batchId()))
              .findFirst();
      if (oldIntent.isPresent()) oldIntent.get().updateOrder(lot.quantity(), lot.unit());
      else
        intents.save(
            com.fabricmanagement.production.core.batch.domain.BatchLotQuantityIntent.forOrder(
                tenant,
                orderId,
                orderNumber,
                lineId,
                actor,
                lot.batchId(),
                lot.quantity(),
                lot.unit()));
    }
    repository.flush();
  }

  @Override
  @Transactional(readOnly = true)
  public java.util.List<UUID> heldPieceIds(UUID lineId) {
    return repository.findByTenantIdAndOwnerLineId(TenantContext.requireTenantId(), lineId).stream()
        .filter(hold -> hold.getStatus() == StockUnitSoftHoldStatus.ACTIVE)
        .map(StockUnitSoftHold::getStockUnitId)
        .toList();
  }

  @Override
  @Transactional
  public void transferQuoteHolds(
      UUID quoteId, UUID quoteLineId, UUID orderId, String orderNumber, UUID orderLineId) {
    UUID tenant = TenantContext.requireTenantId();
    var activeIntents =
        intents.findByTenantIdAndOwnerLineId(tenant, quoteLineId).stream()
            .filter(
                intent ->
                    intent.getStatus()
                        == com.fabricmanagement.production.core.batch.domain
                            .BatchLotQuantityIntentStatus.ACTIVE)
            .toList();
    if (activeIntents.stream()
        .anyMatch(
            intent ->
                !"QUOTE".equals(intent.getOwnerType()) || !quoteId.equals(intent.getQuoteId())))
      throw new com.fabricmanagement.production.core.stockunit.domain.exception
          .StockUnitHeldException();
    ownership.lockLots(
        activeIntents.stream()
            .map(
                com.fabricmanagement.production.core.batch.domain.BatchLotQuantityIntent
                    ::getBatchId)
            .toList());
    var active =
        repository.findByTenantIdAndOwnerLineId(tenant, quoteLineId).stream()
            .filter(hold -> hold.getStatus() == StockUnitSoftHoldStatus.ACTIVE)
            .toList();
    var ids = active.stream().map(StockUnitSoftHold::getStockUnitId).toList();
    var backingLots =
        activeIntents.stream()
            .map(
                com.fabricmanagement.production.core.batch.domain.BatchLotQuantityIntent
                    ::getBatchId)
            .collect(Collectors.toSet());
    var units =
        ids.isEmpty()
            ? java.util.List.<com.fabricmanagement.production.core.stockunit.domain.StockUnit>of()
            : pieces.findByTenantIdAndIdInAndIsActiveTrue(tenant, ids);
    if (units.size() != ids.size()
        || units.stream().anyMatch(piece -> !backingLots.contains(piece.getBatchId())))
      throw new com.fabricmanagement.production.core.stockunit.domain.exception
          .StockUnitHeldException();
    ownership.assertAvailable(
        active.stream().map(StockUnitSoftHold::getStockUnitId).toList(), quoteLineId, null);
    active.forEach(hold -> hold.transferToOrder(orderId, orderLineId));
    activeIntents.forEach(intent -> intent.transferToOrder(orderId, orderNumber, orderLineId));
    repository.flush();
    intents.flush();
  }

  private void placeOrReactivate(UUID tenantId, UUID quoteLineId, UUID stockUnitId) {
    repository
        .findByTenantIdAndQuoteLineIdAndStockUnitId(tenantId, quoteLineId, stockUnitId)
        .ifPresentOrElse(
            existing -> {
              if (existing.getStatus() == StockUnitSoftHoldStatus.RELEASED) {
                existing.reactivate();
                StockUnitSoftHold saved = repository.save(existing);
                eventPublisher.publishEvent(
                    new StockUnitSoftHoldPlacedEvent(
                        tenantId, quoteLineId, stockUnitId, saved.getId()));
              }
            },
            () -> {
              StockUnitSoftHold saved =
                  repository.save(StockUnitSoftHold.place(tenantId, quoteLineId, stockUnitId));
              eventPublisher.publishEvent(
                  new StockUnitSoftHoldPlacedEvent(
                      tenantId, quoteLineId, stockUnitId, saved.getId()));
            });
  }
}
