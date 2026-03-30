package com.fabricmanagement.approval.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fabricmanagement.approval.domain.ApprovalEntityType;
import com.fabricmanagement.approval.domain.ApprovalPolicy;
import com.fabricmanagement.approval.domain.ApprovalRequest;
import com.fabricmanagement.approval.domain.ApproverRole;
import com.fabricmanagement.approval.domain.PolicyTargetLevel;
import com.fabricmanagement.approval.domain.UserTrustLevel;
import com.fabricmanagement.approval.domain.port.UserTrustLevelPort;
import com.fabricmanagement.approval.infra.repository.ApprovalRequestRepository;
import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApprovalGuardService Test")
class ApprovalGuardServiceTest {

  @Mock private ApprovalPolicyService policyService;
  @Mock private ApprovalRequestRepository requestRepo;
  @Mock private UserTrustLevelPort userTrustLevelPort;
  @Mock private ApproverRecipientResolver approverRecipientResolver;
  @Mock private DomainEventPublisher eventPublisher;
  @Spy private Clock clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

  @InjectMocks private ApprovalGuardService guardService;

  @BeforeEach
  void stubApproverRecipients() {
    lenient()
        .when(approverRecipientResolver.resolveUsersForApproverRole(any(), any()))
        .thenReturn(List.of(UUID.randomUUID()));
  }

  private final UUID tenantId = UUID.randomUUID();
  private final UUID userId = UUID.randomUUID();
  private final UUID entityId = UUID.randomUUID();

  @Test
  @DisplayName("Aktif policy yoksa -> onay gerekmez (false)")
  void whenNoPolicy_thenDoesNotRequireApproval() {
    when(policyService.getActivePolicyFor(tenantId, ApprovalEntityType.WORK_ORDER))
        .thenReturn(Optional.empty());

    boolean requiresApproval =
        guardService.checkAndEnforceApproval(
            tenantId, userId, ApprovalEntityType.WORK_ORDER, entityId, 48);

    assertThat(requiresApproval).isFalse();
    verifyNoInteractions(requestRepo);
  }

  @Test
  @DisplayName("PROBATION policy -> PROBATION user: onay gerekir (true)")
  void whenProbationPolicyAndProbationUser_thenRequiresApproval() {
    ApprovalPolicy policy =
        new ApprovalPolicy(
            tenantId,
            ApprovalEntityType.WORK_ORDER,
            PolicyTargetLevel.PROBATION,
            ApproverRole.MANAGER,
            10);
    when(policyService.getActivePolicyFor(tenantId, ApprovalEntityType.WORK_ORDER))
        .thenReturn(Optional.of(policy));

    when(userTrustLevelPort.resolveTrustLevel(tenantId, userId))
        .thenReturn(UserTrustLevel.PROBATION);

    boolean requiresApproval =
        guardService.checkAndEnforceApproval(
            tenantId, userId, ApprovalEntityType.WORK_ORDER, entityId, 48);

    assertThat(requiresApproval).isTrue();

    ArgumentCaptor<ApprovalRequest> captor = ArgumentCaptor.forClass(ApprovalRequest.class);
    verify(requestRepo).save(captor.capture());
    ApprovalRequest savedReq = captor.getValue();
    assertThat(savedReq.getEntityType()).isEqualTo(ApprovalEntityType.WORK_ORDER);
    assertThat(savedReq.getRequestedBy()).isEqualTo(userId);
  }

  @Test
  @DisplayName("PROBATION policy -> STANDARD user: onay GEREKMEZ (false)")
  void whenProbationPolicyAndStandardUser_thenBypasses() {
    ApprovalPolicy policy =
        new ApprovalPolicy(
            tenantId,
            ApprovalEntityType.WORK_ORDER,
            PolicyTargetLevel.PROBATION,
            ApproverRole.MANAGER,
            10);
    when(policyService.getActivePolicyFor(tenantId, ApprovalEntityType.WORK_ORDER))
        .thenReturn(Optional.of(policy));

    when(userTrustLevelPort.resolveTrustLevel(tenantId, userId))
        .thenReturn(UserTrustLevel.STANDARD);

    boolean requiresApproval =
        guardService.checkAndEnforceApproval(
            tenantId, userId, ApprovalEntityType.WORK_ORDER, entityId, 48);

    assertThat(requiresApproval).isFalse();
    verifyNoInteractions(requestRepo);
  }

  @Test
  @DisplayName("ALL policy -> TRYSTED user: yine de onay gerekir (true)")
  void whenAllPolicyAndTrustedUser_thenRequiresApproval() {
    ApprovalPolicy policy =
        new ApprovalPolicy(
            tenantId,
            ApprovalEntityType.CRITICAL_ACTION,
            PolicyTargetLevel.ALL,
            ApproverRole.CEO,
            10);
    when(policyService.getActivePolicyFor(tenantId, ApprovalEntityType.CRITICAL_ACTION))
        .thenReturn(Optional.of(policy));

    when(userTrustLevelPort.resolveTrustLevel(tenantId, userId))
        .thenReturn(UserTrustLevel.TRUSTED); // Trusted olsalar da politika herkese hitap ediyor

    boolean requiresApproval =
        guardService.checkAndEnforceApproval(
            tenantId, userId, ApprovalEntityType.CRITICAL_ACTION, entityId, 48);

    assertThat(requiresApproval).isTrue();
    verify(requestRepo).save(any(ApprovalRequest.class));
  }
}
