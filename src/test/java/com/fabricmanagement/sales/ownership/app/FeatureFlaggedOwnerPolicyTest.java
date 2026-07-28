package com.fabricmanagement.sales.ownership.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fabricmanagement.sales.ownership.domain.OwnerResolution;
import com.fabricmanagement.sales.ownership.domain.OwnerResolutionContext;
import com.fabricmanagement.sales.ownership.domain.OwnerResolutionReason;
import com.fabricmanagement.sales.ownership.domain.OwnershipPolicy;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class FeatureFlaggedOwnerPolicyTest {

  @Mock private AcquirerFirstPolicy acquirerFirstPolicy;
  @Mock private AssignmentFirstPolicy assignmentFirstPolicy;
  @Mock private OwnershipPolicyService ownershipPolicyService;
  @Mock private OwnershipPolicy ownershipPolicy;

  @Test
  void globalKillSwitchKeepsAcquirerFirstPolicyActive() {
    UUID tenantId = UUID.randomUUID();
    OwnerResolutionContext context = new OwnerResolutionContext(tenantId, UUID.randomUUID(), null);
    OwnerResolution legacy = new OwnerResolution(UUID.randomUUID(), OwnerResolutionReason.ACQUIRER);
    FeatureFlaggedOwnerPolicy policy =
        new FeatureFlaggedOwnerPolicy(
            acquirerFirstPolicy, assignmentFirstPolicy, ownershipPolicyService);
    ReflectionTestUtils.setField(policy, "globalAssignmentLadderEnabled", false);
    when(acquirerFirstPolicy.resolve(context)).thenReturn(legacy);

    assertThat(policy.resolve(context)).isEqualTo(legacy);
    verifyNoInteractions(assignmentFirstPolicy);
    verifyNoInteractions(ownershipPolicyService);
  }

  @Test
  void bothFlagsAreRequiredForAssignmentFirstPolicy() {
    UUID tenantId = UUID.randomUUID();
    OwnerResolutionContext context = new OwnerResolutionContext(tenantId, UUID.randomUUID(), null);
    OwnerResolution assignment =
        new OwnerResolution(UUID.randomUUID(), OwnerResolutionReason.PRIMARY_ASSIGNMENT);
    FeatureFlaggedOwnerPolicy policy =
        new FeatureFlaggedOwnerPolicy(
            acquirerFirstPolicy, assignmentFirstPolicy, ownershipPolicyService);
    ReflectionTestUtils.setField(policy, "globalAssignmentLadderEnabled", true);
    when(ownershipPolicyService.requirePolicy(tenantId)).thenReturn(ownershipPolicy);
    when(ownershipPolicy.isAssignmentLadderEnabled()).thenReturn(true);
    when(assignmentFirstPolicy.resolve(context)).thenReturn(assignment);

    assertThat(policy.resolve(context)).isEqualTo(assignment);
    verifyNoInteractions(acquirerFirstPolicy);
  }

  @Test
  void tenantFlagKeepsLegacyPolicyWhenGlobalGateIsAvailable() {
    UUID tenantId = UUID.randomUUID();
    OwnerResolutionContext context = new OwnerResolutionContext(tenantId, UUID.randomUUID(), null);
    OwnerResolution legacy =
        new OwnerResolution(UUID.randomUUID(), OwnerResolutionReason.ACCOUNT_TEAM);
    FeatureFlaggedOwnerPolicy policy =
        new FeatureFlaggedOwnerPolicy(
            acquirerFirstPolicy, assignmentFirstPolicy, ownershipPolicyService);
    ReflectionTestUtils.setField(policy, "globalAssignmentLadderEnabled", true);
    when(ownershipPolicyService.requirePolicy(tenantId)).thenReturn(ownershipPolicy);
    when(ownershipPolicy.isAssignmentLadderEnabled()).thenReturn(false);
    when(acquirerFirstPolicy.resolve(context)).thenReturn(legacy);

    assertThat(policy.resolve(context)).isEqualTo(legacy);
    verifyNoInteractions(assignmentFirstPolicy);
  }
}
