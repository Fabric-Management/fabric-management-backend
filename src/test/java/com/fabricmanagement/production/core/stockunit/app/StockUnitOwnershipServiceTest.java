package com.fabricmanagement.production.core.stockunit.app;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.realtime.app.LiveEditLeaseService;
import com.fabricmanagement.production.core.batch.domain.Batch;
import com.fabricmanagement.production.core.batch.domain.BatchReservation;
import com.fabricmanagement.production.core.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.core.batch.infra.repository.BatchReservationRepository;
import com.fabricmanagement.production.core.stockunit.domain.StockUnit;
import com.fabricmanagement.production.core.stockunit.domain.StockUnitAllocation;
import com.fabricmanagement.production.core.stockunit.domain.StockUnitStatus;
import com.fabricmanagement.production.core.stockunit.domain.exception.StockUnitHeldException;
import com.fabricmanagement.production.core.stockunit.infra.repository.StockUnitAllocationRepository;
import com.fabricmanagement.production.core.stockunit.infra.repository.StockUnitRepository;
import com.fabricmanagement.production.core.stockunit.infra.repository.StockUnitSoftHoldRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StockUnitOwnershipServiceTest {
  @Mock LiveEditLeaseService leases;
  @Mock BatchRepository batches;
  @Mock StockUnitRepository pieces;
  @Mock StockUnitSoftHoldRepository holds;
  @Mock StockUnitAllocationRepository allocations;
  @Mock BatchReservationRepository reservations;
  @Mock EntityManager entityManager;
  @InjectMocks StockUnitOwnershipService service;
  final UUID tenant = UUID.randomUUID(),
      pieceId = UUID.randomUUID(),
      lotId = UUID.randomUUID(),
      workOrder = UUID.randomUUID(),
      product = UUID.randomUUID();

  @BeforeEach
  void setup() {
    TenantContext.setCurrentTenantId(tenant);
    var piece = StockUnit.builder().batchId(lotId).status(StockUnitStatus.RESERVED).build();
    piece.setId(pieceId);
    var lot = Batch.builder().productId(product).build();
    when(pieces.findByTenantIdAndIdInAndIsActiveTrue(tenant, List.of(pieceId)))
        .thenReturn(List.of(piece));
    when(batches.findByTenantIdAndIdInAndIsActiveTrue(eq(tenant), anyCollection()))
        .thenReturn(List.of(lot));
  }

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  @Test
  void legitimateWorkOrderReservationCanBeConsumedUnderTheInventoryGate() {
    var reservation =
        BatchReservation.create(
            tenant,
            lotId,
            workOrder,
            "WORK_ORDER",
            new BigDecimal("30"),
            "KG",
            "Northbridge weaving input");
    when(reservations.findFirstByTenantIdAndBatchIdAndReferenceIdAndStatusInAndIsActiveTrue(
            eq(tenant), eq(lotId), eq(workOrder), anyCollection()))
        .thenReturn(Optional.of(reservation));
    service.assertReservedConsumption(pieceId, workOrder);
    var ordered = inOrder(leases, batches, reservations);
    ordered.verify(leases, times(2)).lock(any());
    ordered.verify(batches).findByIdAndTenantIdForUpdate(lotId, tenant);
    ordered
        .verify(reservations)
        .findFirstByTenantIdAndBatchIdAndReferenceIdAndStatusInAndIsActiveTrue(
            eq(tenant), eq(lotId), eq(workOrder), anyCollection());
  }

  @Test
  void unrelatedWorkOrderCannotUseTheReservedStatusAsAuthority() {
    assertThatThrownBy(() -> service.assertReservedConsumption(pieceId, workOrder))
        .isInstanceOf(StockUnitHeldException.class);
  }

  @Test
  void salesAllocationCannotBeConsumedEvenWithAMatchingWorkOrderIdentifier() {
    var allocation =
        StockUnitAllocation.allocate(
            pieceId,
            lotId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            workOrder,
            new BigDecimal("125"),
            "M",
            UUID.randomUUID(),
            Instant.now());
    when(allocations.findByTenantIdAndStockUnitIdInAndStatus(eq(tenant), anyCollection(), any()))
        .thenReturn(List.of(allocation));
    assertThatThrownBy(() -> service.assertReservedConsumption(pieceId, workOrder))
        .isInstanceOf(StockUnitHeldException.class);
    verifyNoInteractions(reservations);
  }
}
