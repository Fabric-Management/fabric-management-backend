package com.fabricmanagement.flowboard.decision.app.listener;

import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.flowboard.decision.infra.repository.DecisionSubjectProjectionWriter;
import com.fabricmanagement.sales.salesorder.domain.event.*;
import com.fabricmanagement.sales.salesorder.domain.port.OrderCoverProjectionPort;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DecisionProjectionListener {
  private final OrderCoverProjectionPort source;
  private final DecisionSubjectProjectionWriter writer;

  @ApplicationModuleListener
  @Retryable(
      retryFor = TransientDataAccessException.class,
      maxAttempts = 3,
      backoff = @Backoff(delay = 200, multiplier = 2))
  public void onOrderCoverCaseOpened(OrderCoverCaseOpenedEvent event) {
    refresh(event, event.getCaseId());
  }

  @ApplicationModuleListener
  @Retryable(
      retryFor = TransientDataAccessException.class,
      maxAttempts = 3,
      backoff = @Backoff(delay = 200, multiplier = 2))
  public void onOrderCoverCaseChanged(OrderCoverCaseChangedEvent event) {
    refresh(event, event.getCaseId());
  }

  @ApplicationModuleListener
  @Retryable(
      retryFor = TransientDataAccessException.class,
      maxAttempts = 3,
      backoff = @Backoff(delay = 200, multiplier = 2))
  public void onOrderCoverEvidenceRevised(OrderCoverEvidenceRevisedEvent event) {
    refresh(event, event.getCaseId());
  }

  private void refresh(DomainEvent event, UUID caseId) {
    TenantContext.executeInTenantContext(
        event.getTenantId(),
        () -> {
          var facts = source.facts(event.getTenantId(), List.of(caseId));
          if (!facts.isEmpty()) writer.apply(facts.getFirst(), event.getEventId());
        });
  }
}
