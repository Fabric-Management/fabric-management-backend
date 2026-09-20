package com.fabricmanagement.inventory.reservation.app;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.SalesOrderLineFulfilmentLock;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.inventory.reservation.domain.StockReservation;
import com.fabricmanagement.inventory.reservation.infra.repository.StockReservationRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class StockReservationServiceTest {
  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  @Test
  void creationTakesTheSharedLineLockBeforePersistingReservation() {
    UUID tenant = UUID.randomUUID(), line = UUID.randomUUID();
    TenantContext.setCurrentTenantId(tenant);
    StockReservationRepository repository = mock(StockReservationRepository.class);
    SalesOrderLineFulfilmentLock lock = mock(SalesOrderLineFulfilmentLock.class);
    when(repository.save(any()))
        .thenAnswer(
            call -> {
              StockReservation value = call.getArgument(0);
              value.setId(UUID.randomUUID());
              return value;
            });
    var service =
        new StockReservationService(
            repository,
            mock(StockReservationEngine.class),
            mock(ApplicationEventPublisher.class),
            lock);

    service.createReservation(
        line, UUID.randomUUID(), UUID.randomUUID(), "LOT-1", null, BigDecimal.ONE);

    var ordered = inOrder(lock, repository);
    ordered.verify(lock).lock(tenant, line);
    ordered.verify(repository).save(any());
  }
}
