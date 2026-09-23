package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.sales.salesorder.domain.OrderCoverEvidence;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLineStatus;
import com.fabricmanagement.sales.salesorder.domain.port.OrderCoverEvidencePort;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderCoverEvidenceRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderCoverEvidenceStreamRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderLineRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;

class OrderCoverEvidenceLocklessRevalidationTest {
  @AfterEach
  void clearTenant() {
    TenantContext.clear();
  }

  @Test
  void sourceChangeUsesLocklessInspectAndTheSharedFingerprint() {
    UUID tenant = UUID.randomUUID(), orderId = UUID.randomUUID(), caseId = UUID.randomUUID();
    UUID evidenceId = UUID.randomUUID(), lineId = UUID.randomUUID();
    TenantContext.setCurrentTenantId(tenant);
    SalesOrderRepository orders = mock(SalesOrderRepository.class);
    SalesOrderLineRepository lines = mock(SalesOrderLineRepository.class);
    OrderCoverEvidenceRepository evidence = mock(OrderCoverEvidenceRepository.class);
    OrderCoverEvidencePort production = mock(OrderCoverEvidencePort.class);
    SalesOrder order = mock(SalesOrder.class);
    when(order.getIsActive()).thenReturn(true);
    when(order.getVersion()).thenReturn(3L);
    when(orders.findByTenantIdAndId(tenant, orderId)).thenReturn(Optional.of(order));
    SalesOrderLine line = mock(SalesOrderLine.class);
    when(line.getId()).thenReturn(lineId);
    when(line.getVersion()).thenReturn(2L);
    when(line.getCreatedAt()).thenReturn(Instant.parse("2026-09-22T09:00:00Z"));
    when(line.getRequestedQty()).thenReturn(BigDecimal.TEN);
    when(line.getShippedQty()).thenReturn(BigDecimal.ZERO);
    when(line.getUnit()).thenReturn("kg");
    when(line.getProductDesc()).thenReturn("Cotton");
    when(line.getModuleSpecs()).thenReturn(Map.of());
    when(line.getLineStatus()).thenReturn(SalesOrderLineStatus.PENDING);
    when(lines.findByTenantIdAndSalesOrderIdAndIsActiveTrueOrderByCreatedAtAscIdAsc(
            tenant, orderId))
        .thenReturn(List.of(line));
    OrderCoverEvidence previous = mock(OrderCoverEvidence.class);
    when(previous.getCaseId()).thenReturn(caseId);
    when(previous.getInputFingerprint()).thenReturn("fingerprint-before-stock-change");
    when(evidence.findByTenantIdAndSalesOrderIdAndId(tenant, orderId, evidenceId))
        .thenReturn(Optional.of(previous));
    when(production.inspect(any()))
        .thenReturn(new OrderCoverEvidencePort.Inputs(List.of(), List.of()));
    var service =
        new OrderCoverEvidenceService(
            orders,
            lines,
            mock(OrderCoverEvidenceStreamRepository.class),
            evidence,
            production,
            Clock.fixed(Instant.EPOCH, ZoneOffset.UTC),
            mock(PlatformTransactionManager.class),
            mock(OrderCoverObjectAccess.class),
            mock(DomainEventPublisher.class));

    assertThat(service.revalidateLockless(orderId, evidenceId).matches()).isFalse();
    verify(production).inspect(any());
    verify(production, never()).lockAndInspect(any());
  }
}
