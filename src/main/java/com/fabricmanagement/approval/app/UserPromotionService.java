package com.fabricmanagement.approval.app;

import com.fabricmanagement.approval.domain.ApprovalRequestStatus;
import com.fabricmanagement.approval.domain.PromotionRequestStatus;
import com.fabricmanagement.approval.domain.PromotionTriggerType;
import com.fabricmanagement.approval.domain.UserPromotionRequest;
import com.fabricmanagement.approval.domain.port.UserTrustMutationPort;
import com.fabricmanagement.approval.infra.repository.ApprovalRequestRepository;
import com.fabricmanagement.approval.infra.repository.UserPromotionRequestRepository;
import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.user.UserTrustLevel;
import com.fabricmanagement.platform.approval.domain.event.PromotionEscalationEvent;
import com.fabricmanagement.platform.user.domain.SystemUser;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kullanıcı seviyesinin (PROBATION -> STANDARD vb) yükseltilmesini ve 3 Red eskalasyon kurallarını
 * uygulayan servis katmanı.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserPromotionService {

  private final UserPromotionRequestRepository promotionRepo;
  private final ApprovalRequestRepository requestRepo;
  private final UserTrustMutationPort userTrustMutationPort;
  private final DomainEventPublisher eventPublisher;

  /**
   * Bir işlem başarılı onaylandığında çağrılır (ApprovalEvent listener tarafından). Kullanıcının
   * onaylanan işlemleri promotion_threshold'u geçtiyse sistem terfi talebi fırlatır.
   */
  @Transactional
  public void checkAndTriggerPromotion(UUID tenantId, UUID userId, int threshold) {
    if (userId == null || SystemUser.ID.equals(userId)) {
      log.debug(
          "Promotion check skipped: no real requester user (tenant={}, userId={})",
          tenantId,
          userId);
      return;
    }

    UserTrustLevel currentTrustLevel =
        userTrustMutationPort.findTrustLevel(tenantId, userId).orElse(null);
    if (currentTrustLevel == null) {
      log.debug(
          "Promotion check skipped: user not found in tenant (tenant={}, userId={})",
          tenantId,
          userId);
      return;
    }

    // TRUSTED olanların daha yükseleceği yer yok
    if (currentTrustLevel == UserTrustLevel.TRUSTED) return;

    int approvedCount =
        requestRepo.countApprovedRequestsForUser(tenantId, userId, ApprovalRequestStatus.APPROVED);

    if (approvedCount >= threshold) {
      log.info(
          "User {} (Level: {}) reached promotion threshold ({}). Creating UP request...",
          userId,
          currentTrustLevel,
          threshold);

      UserPromotionRequest existing =
          promotionRepo
              .findByTenantIdAndUserIdAndStatusAndDeletedAtIsNull(
                  tenantId, userId, PromotionRequestStatus.PENDING)
              .orElse(null);

      // Zaten bir terfi talebi beklemedeyse yenisi açılmaz
      if (existing == null) {
        // Hedef Level
        UserTrustLevel target =
            currentTrustLevel == UserTrustLevel.PROBATION
                ? UserTrustLevel.STANDARD
                : UserTrustLevel.TRUSTED;

        // Geçmiş rejection sayısını hesapla (eskiden hardcoded 0 idi)
        int currentRejection =
            promotionRepo.countByTenantIdAndUserIdAndStatusAndDeletedAtIsNull(
                tenantId, userId, PromotionRequestStatus.REJECTED);

        UserPromotionRequest promotion =
            new UserPromotionRequest(
                tenantId,
                userId,
                currentTrustLevel,
                target,
                PromotionTriggerType.SYSTEM,
                currentRejection,
                approvedCount);

        try {
          promotionRepo.save(promotion);
        } catch (DataIntegrityViolationException ex) {
          log.warn(
              "Duplicate PENDING promotion request for user={} in tenant={}, skipping.",
              userId,
              tenantId);
        }
      }
    }
  }

  @Transactional
  public void approvePromotion(UUID tenantId, UUID promotionId, UUID adminUserId) {
    UserPromotionRequest req =
        promotionRepo
            .findById(promotionId)
            .filter(r -> r.getTenantId().equals(tenantId))
            .orElseThrow(() -> new IllegalArgumentException("Promotion not found"));

    req.approve(adminUserId);
    promotionRepo.save(req);

    // Asıl user'ın trust level'ı güncelleme
    userTrustMutationPort.upgradeTrustLevel(tenantId, req.getUserId(), req.getToLevel());

    log.info("User {} promoted to {} by Admin {}", req.getUserId(), req.getToLevel(), adminUserId);
  }

  @Transactional
  public void rejectPromotion(UUID tenantId, UUID promotionId, UUID adminUserId, String adminNote) {
    UserPromotionRequest req =
        promotionRepo
            .findById(promotionId)
            .filter(r -> r.getTenantId().equals(tenantId))
            .orElseThrow(() -> new IllegalArgumentException("Promotion not found"));

    int attemptNumber = req.getRejectionCount() + 1;

    // Kural: 2. Redde not zorunlu
    if (attemptNumber >= 2 && (adminNote == null || adminNote.isBlank())) {
      throw new IllegalArgumentException(
          "Admin note is strictly required on 2nd or further rejections.");
    }

    req.reject(adminUserId, adminNote);
    promotionRepo.save(req);

    log.warn(
        "User {} promotion to {} REJECTED ({}. attempt). Note: {}",
        req.getUserId(),
        req.getToLevel(),
        attemptNumber,
        adminNote);

    // Kural: 3. Redde hesap askıya alınacak ve HR bildirimi tetiklenecek
    if (attemptNumber >= 3) {
      log.error("ESCALATION: User {} rejected 3 times! Suspending account...", req.getUserId());
      userTrustMutationPort.deactivateUser(
          tenantId, req.getUserId(), "System: 3 times promotion rejection escalation");

      eventPublisher.publish(
          new PromotionEscalationEvent(
              tenantId, req.getUserId(), attemptNumber, "3 times promotion rejection"));
    }
  }

  @Transactional(readOnly = true)
  public List<UserPromotionRequest> getPendingPromotions(UUID tenantId) {
    return promotionRepo.findByTenantIdAndStatusAndDeletedAtIsNull(
        tenantId, PromotionRequestStatus.PENDING);
  }
}
