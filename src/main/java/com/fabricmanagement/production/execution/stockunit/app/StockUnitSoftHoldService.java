package com.fabricmanagement.production.execution.stockunit.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.production.execution.stockunit.api.StockUnitSoftHoldPort;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnitSoftHold;
import com.fabricmanagement.production.execution.stockunit.domain.StockUnitSoftHoldStatus;
import com.fabricmanagement.production.execution.stockunit.domain.event.StockUnitSoftHoldPlacedEvent;
import com.fabricmanagement.production.execution.stockunit.domain.event.StockUnitSoftHoldReleasedEvent;
import com.fabricmanagement.production.execution.stockunit.infra.repository.StockUnitSoftHoldRepository;
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
  private final ApplicationEventPublisher eventPublisher;

  @Override
  @Transactional
  public void replaceHolds(UUID quoteLineId, Collection<UUID> stockUnitIds) {
    UUID tenantId = TenantContext.requireTenantId();
    Set<UUID> requested =
        stockUnitIds == null ? Set.of() : stockUnitIds.stream().collect(Collectors.toSet());
    repository
        .findByTenantIdAndQuoteLineId(tenantId, quoteLineId)
        .forEach(
            existing -> {
              if (!requested.contains(existing.getStockUnitId())
                  && existing.getStatus() == StockUnitSoftHoldStatus.ACTIVE
                  && existing.release(Instant.now())) {
                repository.save(existing);
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
    repository.findByTenantIdAndQuoteLineId(tenantId, quoteLineId).stream()
        .filter(existing -> existing.getStatus() == StockUnitSoftHoldStatus.ACTIVE)
        .forEach(
            existing -> {
              if (existing.release(Instant.now())) {
                repository.save(existing);
                eventPublisher.publishEvent(
                    new StockUnitSoftHoldReleasedEvent(
                        tenantId, quoteLineId, existing.getStockUnitId(), existing.getId()));
              }
            });
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
