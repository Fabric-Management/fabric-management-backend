package com.fabricmanagement.common.infrastructure.events;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class FollowUpFeedbackService {

  private final IncompleteFollowUpFlagRepository flagRepository;
  private final StuckEventFeedbackSender feedbackSender;

  @Transactional
  public void report(UUID flagId) {
    UUID tenantId = TenantContext.requireTenantId();
    IncompleteFollowUpFlag flag =
        flagRepository
            .findByTenantIdAndIdForUpdate(tenantId, flagId)
            .orElseThrow(() -> new NotFoundException("Follow-up flag not found: " + flagId));

    if (flag.getFeedbackReportedAt() != null) {
      return;
    }

    feedbackSender.sendOpsReport(
        new FollowUpFeedbackReport(
            tenantId,
            flag.getPublicationId(),
            flag.getEventType(),
            flag.getEntityType(),
            flag.getEntityRef(),
            flag.getSummary(),
            flag.getReferenceType(),
            flag.getReferenceId(),
            flag.getAffectedUserId(),
            flag.getCreatedAt()));
    flag.markFeedbackReported(Instant.now());
    flagRepository.save(flag);
  }
}
