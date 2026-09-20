package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.sales.salesorder.domain.OrderCoverCase;
import com.fabricmanagement.sales.salesorder.domain.OrderCoverRegime;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import com.fabricmanagement.sales.salesorder.domain.event.OrderCoverCaseOpenedEvent;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderCoverActivationRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderCoverCaseLineRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderCoverCaseRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrderCoverEnrolmentServiceTest {
  private final UUID tenantId = UUID.randomUUID();
  private final OrderCoverActivationRepository activations =
      mock(OrderCoverActivationRepository.class);
  private final OrderCoverCaseRepository cases = mock(OrderCoverCaseRepository.class);
  private final OrderCoverCaseLineRepository caseLines = mock(OrderCoverCaseLineRepository.class);
  private final DomainEventPublisher events = mock(DomainEventPublisher.class);
  private final OrderCoverEnrolmentService service =
      new OrderCoverEnrolmentService(activations, cases, caseLines, events);

  @BeforeEach
  void setContext() {
    TenantContext.setCurrentTenantId(tenantId);
  }

  @AfterEach
  void clearContext() {
    TenantContext.clear();
  }

  @Test
  void nullAndEqualCreationSequencesRemainLegacy() {
    when(activations.find(tenantId)).thenReturn(Optional.of(activation(10)));
    SalesOrder nullSequence = order(null);
    SalesOrder equalSequence = order(10L);

    assertThat(service.decide(nullSequence, List.of())).isEqualTo(OrderCoverRegime.LEGACY);
    assertThat(service.decide(equalSequence, List.of())).isEqualTo(OrderCoverRegime.LEGACY);

    verifyNoInteractions(cases, caseLines, events);
  }

  @Test
  void creationSequenceAboveBoundaryCreatesCaseAndUnresolvedScopeOnce() {
    when(activations.find(tenantId)).thenReturn(Optional.of(activation(10)));
    SalesOrder order = order(11L);
    SalesOrderLine line = mock(SalesOrderLine.class);
    UUID lineId = UUID.randomUUID();
    when(line.getId()).thenReturn(lineId);
    OrderCoverCase coverCase = OrderCoverCase.open(tenantId, order.getId());
    coverCase.setId(UUID.randomUUID());
    when(cases.saveAndFlush(any())).thenReturn(coverCase);

    assertThat(service.decide(order, List.of(line))).isEqualTo(OrderCoverRegime.GOVERNED);
    assertThat(service.decide(order, List.of(line))).isEqualTo(OrderCoverRegime.GOVERNED);

    verify(cases).saveAndFlush(any());
    verify(caseLines).save(any());
    verify(events).publish(any(OrderCoverCaseOpenedEvent.class));
  }

  @Test
  void absentActivationAlwaysSelectsLegacy() {
    when(activations.find(tenantId)).thenReturn(Optional.empty());

    assertThat(service.decide(order(Long.MAX_VALUE), List.of())).isEqualTo(OrderCoverRegime.LEGACY);

    verify(cases, never()).saveAndFlush(any());
  }

  private OrderCoverActivationRepository.Activation activation(long boundary) {
    return new OrderCoverActivationRepository.Activation(
        tenantId, boundary, Instant.parse("2026-09-19T12:00:00Z"), UUID.randomUUID());
  }

  private SalesOrder order(Long creationSequence) {
    SalesOrder order =
        SalesOrder.builder().creationSeq(creationSequence).orderNumber("SO-1").build();
    order.setId(UUID.randomUUID());
    return order;
  }
}
