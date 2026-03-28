package com.fabricmanagement.approval.app.listener;

import com.fabricmanagement.approval.app.ApprovalPolicyService;
import com.fabricmanagement.approval.app.UserPromotionService;
import com.fabricmanagement.approval.domain.ApprovalEntityType;
import com.fabricmanagement.platform.approval.domain.event.ApprovalApprovedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalEventListener {

  private final ApprovalPolicyService policyService;
  private final UserPromotionService promotionService;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Async
  @Retryable(
      retryFor = TransientDataAccessException.class,
      maxAttempts = 3,
      backoff = @Backoff(delay = 500, multiplier = 2))
  public void onApprovalApproved(ApprovalApprovedEvent event) {
    ApprovalEntityType entityType;
    try {
      entityType = ApprovalEntityType.valueOf(event.getEntityType());
    } catch (IllegalArgumentException e) {
      log.debug(
          "ApprovalEventListener: unknown entityType '{}', skipping promotion check.",
          event.getEntityType());
      return;
    }

    policyService
        .getActivePolicyFor(event.getTenantId(), entityType)
        .ifPresentOrElse(
            policy -> {
              log.debug(
                  "ApprovalApproved for tenant={} user={} entityType={} — checking promotion (threshold={}).",
                  event.getTenantId(),
                  event.getRequesterId(),
                  entityType,
                  policy.getPromotionThreshold());
              promotionService.checkAndTriggerPromotion(
                  event.getTenantId(), event.getRequesterId(), policy.getPromotionThreshold());
            },
            () ->
                log.debug(
                    "No active policy for entityType={} in tenant={}, skipping promotion check.",
                    entityType,
                    event.getTenantId()));
  }

  @Recover
  public void recoverApprovalApproved(Exception ex, ApprovalApprovedEvent event) {
    log.error(
        "Failed to process ApprovalApprovedEvent after retries for request={}: {}",
        event.getApprovalRequestId(),
        ex.getMessage(),
        ex);
  }
}
