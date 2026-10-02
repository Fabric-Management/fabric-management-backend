package com.fabricmanagement.sales.orderintake.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.sales.orderintake.domain.AcceptanceTerms;
import com.fabricmanagement.sales.orderintake.domain.CustomerProductRequest;
import com.fabricmanagement.sales.orderintake.domain.CustomerRequestDecisionOutcome;
import com.fabricmanagement.sales.orderintake.infra.repository.CustomerProductRequestRepository;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** SOI R18, A10, A11: custom-request conditions of confirmation. */
class CustomRequestGateTest {

  private static final UUID TENANT = UUID.randomUUID();
  private final CustomerProductRequestRepository requests =
      mock(CustomerProductRequestRepository.class);
  private final CustomRequestGate gate = new CustomRequestGate(requests);
  private SalesOrder order;
  private SalesOrderLine line;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT);
    order = SalesOrder.builder().tradingPartnerId(UUID.randomUUID()).orderNumber("SO-9").build();
    order.setId(UUID.randomUUID());
    line =
        SalesOrderLine.builder()
            .salesOrderId(order.getId())
            .productId(UUID.randomUUID())
            .requestedQty(new BigDecimal("300"))
            .unit("M")
            .build();
    line.setId(UUID.randomUUID());
    when(requests.findByTenantIdAndResolvedLineIdInAndIsActiveTrue(eq(TENANT), any()))
        .thenReturn(List.of());
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  @DisplayName("S11/S19: an unfinished request on the order blocks its confirmation")
  void openRequestBlocks() {
    when(requests.findByTenantIdAndSalesOrderIdAndIsActiveTrueOrderByRecordedAtAscIdAsc(
            TENANT, order.getId()))
        .thenReturn(List.of(request(order.getId())));

    assertThat(gate.blocks(order, List.of(line)))
        .extracting(ConfirmationGate.Block::code)
        .containsExactly("CUSTOM_REQUEST_OPEN");
  }

  @Test
  @DisplayName("S21: a line made from a request needs the approval of its current terms")
  void resolvedLineNeedsCurrentApproval() {
    CustomerProductRequest resolved = request(order.getId());
    resolved.nextRevision();
    resolved.decided(CustomerRequestDecisionOutcome.APPROVED, resolved.requestTerms());
    resolved.resolve(line.getId());
    when(requests.findByTenantIdAndSalesOrderIdAndIsActiveTrueOrderByRecordedAtAscIdAsc(
            TENANT, order.getId()))
        .thenReturn(List.of(resolved));
    when(requests.findByTenantIdAndResolvedLineIdInAndIsActiveTrue(eq(TENANT), any()))
        .thenReturn(List.of(resolved));

    assertThat(gate.blocks(order, List.of(line)))
        .extracting(ConfirmationGate.Block::code)
        .containsExactly("SAMPLE_APPROVAL_STALE");
    resolved.decided(CustomerRequestDecisionOutcome.APPROVED, AcceptanceTerms.fingerprint(line));
    assertThat(gate.blocks(order, List.of(line))).isEmpty();

    line.setRequestedQty(new BigDecimal("320"));
    assertThat(gate.blocks(order, List.of(line)))
        .extracting(ConfirmationGate.Block::code)
        .containsExactly("SAMPLE_APPROVAL_STALE");
  }

  private CustomerProductRequest request(UUID orderId) {
    return CustomerProductRequest.record(
        order.getTradingPartnerId(),
        orderId,
        new CustomerProductRequest.Details(
            "Navy twill as the sample",
            null,
            new BigDecimal("300"),
            "M",
            null,
            null,
            null,
            null,
            null,
            null),
        false,
        UUID.randomUUID(),
        Instant.parse("2026-09-27T09:00:00Z"));
  }
}
