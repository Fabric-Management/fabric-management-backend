package com.fabricmanagement.approval.app;

import com.fabricmanagement.approval.domain.ApprovalRequest;
import com.fabricmanagement.approval.domain.ApprovalRequestStatus;
import com.fabricmanagement.approval.domain.ApproverRole;
import com.fabricmanagement.approval.infra.repository.ApprovalRequestRepository;
import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.platform.approval.domain.event.ApprovalApprovedEvent;
import com.fabricmanagement.platform.approval.domain.event.ApprovalRejectedEvent;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Onaylanmayı (veya Reddedilmeyi) bekleyen ApprovalRequest nesnelerinin işlendiği katman. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalRequestService {

  private final ApprovalRequestRepository requestRepo;
  private final DomainEventPublisher eventPublisher;
  private final Clock clock;

  /**
   * Beklemedeki onay taleplerini getirir. approverRole belirtilmişse, sadece o role atanmış
   * policy'lere ait talepler filtrelenir.
   *
   * @param tenantId Aktif tenant
   * @param approverRole İsteğe bağlı. Belirtilirse sadece bu role ait talepler döner.
   */
  @Transactional(readOnly = true)
  public List<ApprovalRequest> getPendingRequests(UUID tenantId, ApproverRole approverRole) {
    if (approverRole != null) {
      return requestRepo.findPendingRequestsByApproverRole(
          tenantId, ApprovalRequestStatus.PENDING, approverRole);
    }
    return requestRepo.findByTenantIdAndStatusAndDeletedAtIsNull(
        tenantId, ApprovalRequestStatus.PENDING);
  }

  /** Geriye uyumluluk: filtre olmadan tüm pending request'leri getirir. */
  @Transactional(readOnly = true)
  public List<ApprovalRequest> getPendingRequests(UUID tenantId) {
    return getPendingRequests(tenantId, (ApproverRole) null);
  }

  @Transactional
  public void approveRequest(UUID tenantId, UUID requestId, UUID approverUserId) {
    ApprovalRequest request =
        requestRepo
            .findById(requestId)
            .filter(r -> r.getTenantId().equals(tenantId))
            .orElseThrow(() -> new IllegalArgumentException("Approval request not found"));

    request.approve(approverUserId, OffsetDateTime.now(clock));
    requestRepo.save(request);

    log.info("Approval Request {} is APPROVED by {}", requestId, approverUserId);

    eventPublisher.publish(
        new ApprovalApprovedEvent(
            tenantId,
            requestId,
            request.getEntityType().name(),
            request.getEntityId(),
            request.getUid(),
            request.getRequestedBy()));
  }

  @Transactional
  public void rejectRequest(UUID tenantId, UUID requestId, UUID approverUserId, String reason) {
    ApprovalRequest request =
        requestRepo
            .findById(requestId)
            .filter(r -> r.getTenantId().equals(tenantId))
            .orElseThrow(() -> new IllegalArgumentException("Approval request not found"));

    request.reject(approverUserId, reason);
    requestRepo.save(request);

    log.info(
        "Approval Request {} is REJECTED by {}, Reason: {}", requestId, approverUserId, reason);

    eventPublisher.publish(
        new ApprovalRejectedEvent(
            tenantId,
            requestId,
            request.getEntityType().name(),
            request.getEntityId(),
            request.getUid(),
            request.getRequestedBy(),
            reason));
  }
}
