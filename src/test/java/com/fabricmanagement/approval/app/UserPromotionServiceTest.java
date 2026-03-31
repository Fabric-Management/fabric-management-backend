package com.fabricmanagement.approval.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.approval.domain.PromotionRequestStatus;
import com.fabricmanagement.approval.domain.PromotionTriggerType;
import com.fabricmanagement.approval.domain.UserPromotionRequest;
import com.fabricmanagement.approval.domain.port.UserTrustMutationPort;
import com.fabricmanagement.approval.infra.repository.ApprovalRequestRepository;
import com.fabricmanagement.approval.infra.repository.UserPromotionRequestRepository;
import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.user.UserTrustLevel;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserPromotionService Test")
class UserPromotionServiceTest {

  @Mock private UserPromotionRequestRepository promotionRepo;
  @Mock private ApprovalRequestRepository requestRepo;
  @Mock private UserTrustMutationPort userTrustMutationPort;
  @Mock private DomainEventPublisher eventPublisher;

  @InjectMocks private UserPromotionService promotionService;

  private final UUID tenantId = UUID.randomUUID();
  private final UUID userId = UUID.randomUUID();
  private final UUID adminId = UUID.randomUUID();

  @Test
  @DisplayName("2. Red işlemi: Not boş bırakılamaz")
  void whenSecondRejectionWithoutNote_thenThrowsError() {
    UUID promotionId = UUID.randomUUID();

    UserPromotionRequest req =
        new UserPromotionRequest(
            tenantId,
            userId,
            UserTrustLevel.PROBATION,
            UserTrustLevel.STANDARD,
            PromotionTriggerType.SYSTEM,
            1, // Daha önce 1 kez reddedilmiş -> bu 2. denemesi olacak
            10);
    ReflectionTestUtils.setField(req, "id", promotionId);

    when(promotionRepo.findById(promotionId)).thenReturn(Optional.of(req));

    Throwable t =
        catchThrowable(() -> promotionService.rejectPromotion(tenantId, promotionId, adminId, ""));

    assertThat(t)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Admin note is strictly required");
  }

  @Test
  @DisplayName("3. Red işlemi: Kullanıcı askıya alınır (Eskalasyon)")
  void whenThirdRejection_thenSuspendsAccount() {
    UUID promotionId = UUID.randomUUID();

    UserPromotionRequest req =
        new UserPromotionRequest(
            tenantId,
            userId,
            UserTrustLevel.PROBATION,
            UserTrustLevel.STANDARD,
            PromotionTriggerType.SYSTEM,
            2, // Daha önce 2 kez reddedilmiş -> bu 3. denemesi olacak
            10);
    ReflectionTestUtils.setField(req, "id", promotionId);

    when(promotionRepo.findById(promotionId)).thenReturn(Optional.of(req));

    promotionService.rejectPromotion(tenantId, promotionId, adminId, "Hala kurallara uymuyor.");

    assertThat(req.getStatus()).isEqualTo(PromotionRequestStatus.REJECTED);
    assertThat(req.getAdminNote()).isEqualTo("Hala kurallara uymuyor.");

    // Eskalasyon kuralı gereği servis hesabın dondurulması için Port'u çağırmalı
    verify(userTrustMutationPort).deactivateUser(eq(tenantId), eq(userId), anyString());
  }
}
