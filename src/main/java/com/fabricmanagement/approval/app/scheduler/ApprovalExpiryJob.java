package com.fabricmanagement.approval.app.scheduler;

import com.fabricmanagement.approval.domain.ApprovalRequest;
import com.fabricmanagement.approval.infra.repository.ApprovalRequestRepository;
import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.platform.approval.domain.event.ApprovalExpiredEvent;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Bekleyen Onayların zaman aşımı iptalini(Cancelled) sağlayan saatlik arka plan görevi. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalExpiryJob {

  private final ApprovalRequestRepository requestRepo;
  private final DomainEventPublisher eventPublisher;
  private final Clock clock;

  @Scheduled(fixedRateString = "${approval.expiry.interval-ms:3600000}")
  @Transactional
  public void cancelExpiredRequests() {
    log.info("Starting ApprovalExpiryJob to find and cancel expired requests...");
    OffsetDateTime now = OffsetDateTime.now(clock);

    List<ApprovalRequest> expired = requestRepo.findExpiredPendingRequests(now);

    if (!expired.isEmpty()) {
      expired.forEach(ApprovalRequest::cancel);
      requestRepo.saveAll(expired);
      log.warn("ApprovalExpiryJob cancelled {} EXPIRED requests.", expired.size());

      // Tenant bazlı batch event fırlat
      Map<UUID, List<ApprovalRequest>> byTenant =
          expired.stream().collect(Collectors.groupingBy(ApprovalRequest::getTenantId));

      byTenant.forEach(
          (tenantId, requests) -> {
            List<UUID> ids = requests.stream().map(ApprovalRequest::getId).toList();
            eventPublisher.publish(new ApprovalExpiredEvent(tenantId, ids));
          });
    } else {
      log.debug("No expired approval requests found.");
    }
  }
}
