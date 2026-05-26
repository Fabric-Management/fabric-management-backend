package com.fabricmanagement.approval.app;

import com.fabricmanagement.approval.domain.ApprovalEntityType;
import com.fabricmanagement.approval.domain.ApprovalPolicy;
import com.fabricmanagement.approval.domain.ApprovalRequest;
import com.fabricmanagement.approval.domain.ApprovalRequestStatus;
import com.fabricmanagement.approval.domain.PolicyTargetLevel;
import com.fabricmanagement.approval.infra.repository.ApprovalRequestRepository;
import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.user.UserTrustLevel;
import com.fabricmanagement.platform.approval.domain.event.ApprovalPendingEvent;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Diğer modüllerin (Örn: WorkOrderService) kritik işlemleri esnasında çağırdığı Guard sınıfı.
 * İlgili operation'un PENDING_APPROVAL'a düşüp düşmemesi gerektiğine karar verir.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalGuardService {

  private final ApprovalPolicyService policyService;
  private final ApprovalRequestRepository requestRepo;
  private final com.fabricmanagement.approval.domain.port.UserTrustLevelPort userTrustLevelPort;
  private final ApproverRecipientResolver approverRecipientResolver;
  private final DomainEventPublisher eventPublisher;
  private final Clock clock;

  /**
   * Bir işlem onay gerektiriyorsa ApprovalRequest oluşturur ve true döner. Eğer gerekmiyorsa false
   * döner.
   *
   * @param tenantId Aktif Tenant
   * @param userId İşlemi yapan kişi
   * @param entityType Hangi modül? {@link ApprovalEntityType#WORK_ORDER}, {@link
   *     ApprovalEntityType#RECIPE_CREATE} vs.
   * @param entityId Yaratılan/Güncellenen hedef entity Id'si.
   * @param expiresHours İsteğin kaç saat sonra iptal olacağı (Örn: 48)
   * @return Onay gerektirdi mi? (True ise çağıran taraf işlemi PENDING statüsünde bekletmeli)
   */
  @Transactional
  public boolean checkAndEnforceApproval(
      UUID tenantId, UUID userId, ApprovalEntityType entityType, UUID entityId, int expiresHours) {
    return checkAndEnforceApproval(
        tenantId, userId, entityType, entityId, expiresHours, null, null);
  }

  /**
   * Bir işlem onay gerektiriyorsa ApprovalRequest oluşturur ve true döner. Eğer gerekmiyorsa false
   * döner. (Tutar eşikli varyant)
   */
  @Transactional
  public boolean checkAndEnforceApproval(
      UUID tenantId,
      UUID userId,
      ApprovalEntityType entityType,
      UUID entityId,
      int expiresHours,
      java.math.BigDecimal amount,
      String currency) {

    // 1. Policy var mı? (Yoksa direkt geçer)
    ApprovalPolicy policy =
        policyService.getActivePolicyFor(tenantId, entityType, amount, currency).orElse(null);
    if (policy == null) {
      log.debug("No active policy found for {} in tenant {}, continuing", entityType, tenantId);
      return false;
    }

    // 2. Kullanıcının trust_level'i bu kuralın radarına giriyor mu?
    UserTrustLevel trustLevel = userTrustLevelPort.resolveTrustLevel(tenantId, userId);

    if (!isUserMatchingPolicyLevel(trustLevel, policy.getRequiredForLevel())) {
      log.debug("User {} ({}) bypasses policy {}", userId, trustLevel, policy.getId());
      return false;
    }

    // 3. Zaten PENDING bir istek var mı? (İdempotency)
    boolean alreadyPending =
        requestRepo
            .findByTenantIdAndEntityTypeAndEntityIdAndStatusAndDeletedAtIsNull(
                tenantId, entityType, entityId, ApprovalRequestStatus.PENDING)
            .isPresent();
    if (alreadyPending) {
      log.debug("Already pending request for {} - {}, skipping duplicate.", entityType, entityId);
      return true;
    }

    // 4. Demek ki onaya düşmesi gerekiyor. ApprovalRequest yaz.
    log.info(
        "User {} ({}) requires approval for {} - {}. Policy: {}",
        userId,
        trustLevel,
        entityType,
        entityId,
        policy.getId());

    OffsetDateTime expiresAt = OffsetDateTime.now(clock).plusHours(expiresHours);

    ApprovalRequest request =
        new ApprovalRequest(tenantId, entityType, entityId, policy, userId, expiresAt);

    requestRepo.save(request);

    var notifyRecipients =
        approverRecipientResolver.resolveUsersForApproverRole(tenantId, policy.getApproverRole());
    if (notifyRecipients.isEmpty()) {
      log.warn(
          "Approval pending for {} {} but no notify recipients resolved for approverRole={} —"
              + " notification skipped.",
          entityType,
          entityId,
          policy.getApproverRole());
    }

    eventPublisher.publish(
        new ApprovalPendingEvent(
            tenantId,
            request.getId(),
            entityType.name(),
            entityId,
            request.getUid(),
            null,
            notifyRecipients));

    return true; // "Evet, onay gerektiriyor" olarak anla ve entity'ni (Örn WO) PENDING yap.
  }

  private boolean isUserMatchingPolicyLevel(
      UserTrustLevel userLevel, PolicyTargetLevel policyLevel) {
    if (policyLevel == PolicyTargetLevel.ALL) return true;
    if (policyLevel == PolicyTargetLevel.PROBATION && userLevel == UserTrustLevel.PROBATION)
      return true;
    if (policyLevel == PolicyTargetLevel.STANDARD
        && (userLevel == UserTrustLevel.PROBATION || userLevel == UserTrustLevel.STANDARD))
      return true;

    return false; // TRUSTED ise vs. geçebilir
  }
}
