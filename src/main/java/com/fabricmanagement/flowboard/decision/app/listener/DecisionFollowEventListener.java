package com.fabricmanagement.flowboard.decision.app.listener;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.flowboard.decision.domain.DecisionFollowSource;
import com.fabricmanagement.flowboard.decision.domain.DecisionFollowSourcePolicy;
import com.fabricmanagement.flowboard.decision.infra.repository.DecisionFollowRepository;
import com.fabricmanagement.flowboard.task.domain.event.TaskAssignedEvent;
import com.fabricmanagement.platform.user.api.facade.UserFacade;
import com.fabricmanagement.platform.user.domain.SystemUser;
import com.fabricmanagement.sales.salesorder.domain.event.OrderCoverCaseChangedEvent;
import com.fabricmanagement.sales.salesorder.domain.event.OrderCoverCaseOpenedEvent;
import com.fabricmanagement.sales.salesorder.domain.port.OrderCoverFollowQueryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

/** Durable, idempotent production of the three automatic decision-follow reasons. */
@Component
@RequiredArgsConstructor
public class DecisionFollowEventListener {
  private final DecisionFollowRepository follows;
  private final OrderCoverFollowQueryPort sources;
  private final UserFacade users;

  @ApplicationModuleListener
  @Retryable(
      retryFor = TransientDataAccessException.class,
      maxAttempts = 3,
      backoff = @Backoff(delay = 200, multiplier = 2))
  public void onOrderCoverCaseOpened(OrderCoverCaseOpenedEvent event) {
    TenantContext.executeInTenantContext(
        event.getTenantId(),
        () ->
            sources
                .orderCreator(event.getTenantId(), event.getCaseId())
                .filter(
                    userId ->
                        DecisionFollowSourcePolicy.acceptsOpened(
                            userId, users.isActive(event.getTenantId(), userId), SystemUser.ID))
                .ifPresent(
                    userId ->
                        follows.record(
                            event.getTenantId(),
                            event.getCaseId(),
                            userId,
                            DecisionFollowSource.OPENED,
                            null)));
  }

  @ApplicationModuleListener
  @Retryable(
      retryFor = TransientDataAccessException.class,
      maxAttempts = 3,
      backoff = @Backoff(delay = 200, multiplier = 2))
  public void onTaskAssigned(TaskAssignedEvent event) {
    if (!DecisionFollowSourcePolicy.acceptsAssignment(event.getAssignedUserId(), SystemUser.ID))
      return;
    TenantContext.executeInTenantContext(
        event.getTenantId(),
        () ->
            sources
                .caseForTask(event.getTenantId(), event.getTaskId())
                .ifPresent(
                    caseId ->
                        follows.record(
                            event.getTenantId(),
                            caseId,
                            event.getAssignedUserId(),
                            DecisionFollowSource.ASSIGNED,
                            event.getAssignmentId())));
  }

  @ApplicationModuleListener
  @Retryable(
      retryFor = TransientDataAccessException.class,
      maxAttempts = 3,
      backoff = @Backoff(delay = 200, multiplier = 2))
  public void onOrderCoverCaseChanged(OrderCoverCaseChangedEvent event) {
    if (event.getResultId() == null) return;
    TenantContext.executeInTenantContext(
        event.getTenantId(),
        () ->
            sources
                .settlement(event.getTenantId(), event.getCaseId(), event.getResultId())
                .filter(
                    source ->
                        DecisionFollowSourcePolicy.acceptsSettlement(
                            source.actorKind(), source.actorId(), SystemUser.ID))
                .ifPresent(
                    source ->
                        follows.record(
                            event.getTenantId(),
                            event.getCaseId(),
                            source.actorId(),
                            DecisionFollowSource.SETTLED,
                            source.resultId())));
  }
}
