package com.fabricmanagement.approval.app.scheduler;

import com.fabricmanagement.approval.domain.ApprovalRequest;
import com.fabricmanagement.approval.domain.ApprovalRequestStatus;
import com.fabricmanagement.approval.infra.repository.ApprovalRequestRepository;
import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.approval.domain.event.ApprovalExpiredEvent;
import com.fabricmanagement.platform.tenant.app.TenantSystemService;
import com.fabricmanagement.platform.tenant.dto.TenantDto;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Bekleyen Onayların zaman aşımı iptalini(Cancelled) sağlayan saatlik arka plan görevi. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalExpiryJob {

  private final ApprovalRequestRepository requestRepo;
  private final DomainEventPublisher eventPublisher;
  private final Clock clock;
  private final TenantSystemService tenantService;

  @Scheduled(fixedRateString = "${approval.expiry.interval-ms:3600000}")
  public void cancelExpiredRequests() {
    log.info("Starting ApprovalExpiryJob to find and cancel expired requests...");
    OffsetDateTime now = OffsetDateTime.now(clock);

    List<TenantDto> activeTenants = tenantService.getAllActive();

    for (TenantDto tenant : activeTenants) {
      UUID tenantId = tenant.getId();
      try {
        TenantContext.setCurrentTenantId(tenantId);
        List<ApprovalRequest> expired =
            requestRepo.findExpiredPendingRequests(ApprovalRequestStatus.PENDING, now);

        if (!expired.isEmpty()) {
          expired.forEach(ApprovalRequest::cancel);
          requestRepo.saveAll(expired);
          log.warn(
              "ApprovalExpiryJob cancelled {} EXPIRED requests for tenant {}.",
              expired.size(),
              tenantId);

          List<UUID> ids = expired.stream().map(ApprovalRequest::getId).toList();
          eventPublisher.publish(new ApprovalExpiredEvent(tenantId, ids));
        } else {
          log.debug("No expired approval requests found for tenant {}.", tenantId);
        }
      } catch (Exception e) {
        log.error(
            "Failed to cancel expired requests for tenant {}: {}", tenantId, e.getMessage(), e);
      } finally {
        TenantContext.clear();
      }
    }
  }
}
