package com.fabricmanagement.sales.ownership.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fabricmanagement.platform.user.app.UserQueryService;
import com.fabricmanagement.platform.user.dto.UserDto;
import com.fabricmanagement.sales.ownership.domain.ActorRef;
import com.fabricmanagement.sales.ownership.domain.AssignmentSource;
import com.fabricmanagement.sales.ownership.domain.CustomerCommercialAssignment;
import com.fabricmanagement.sales.ownership.domain.OwnerResolution;
import com.fabricmanagement.sales.ownership.domain.OwnerResolutionContext;
import com.fabricmanagement.sales.ownership.domain.OwnerResolutionReason;
import com.fabricmanagement.sales.ownership.domain.OwnershipMode;
import com.fabricmanagement.sales.ownership.domain.OwnershipPolicy;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AssignmentFirstPolicyTest {

  @Mock private CustomerEligibilityService customerEligibilityService;
  @Mock private CustomerCommercialAssignmentService assignmentService;
  @Mock private OwnershipPolicyService ownershipPolicyService;
  @Mock private UserQueryService userQueryService;
  @Mock private AcquirerFirstPolicy acquirerFirstPolicy;
  @Mock private OwnershipPolicy ownershipPolicy;

  private AssignmentFirstPolicy policy;
  private UUID tenantId;
  private UUID customerId;

  @BeforeEach
  void setUp() {
    policy =
        new AssignmentFirstPolicy(
            customerEligibilityService,
            assignmentService,
            ownershipPolicyService,
            userQueryService,
            acquirerFirstPolicy);
    tenantId = UUID.randomUUID();
    customerId = UUID.randomUUID();
    when(customerEligibilityService.requireEligible(tenantId, customerId))
        .thenReturn(new CustomerEligibilityService.EligibleCustomer(customerId, null));
  }

  @Test
  void explicitOverrideWinsBeforePrimaryAssignment() {
    UUID primaryId = UUID.randomUUID();
    UUID overrideId = UUID.randomUUID();
    when(assignmentService.findCurrent(tenantId, customerId))
        .thenReturn(Optional.of(assignment(primaryId)));
    when(userQueryService.findById(tenantId, primaryId))
        .thenReturn(Optional.of(activeUser(primaryId)));
    when(acquirerFirstPolicy.resolve(new OwnerResolutionContext(tenantId, customerId, overrideId)))
        .thenReturn(new OwnerResolution(overrideId, OwnerResolutionReason.EXPLICIT_OVERRIDE));

    OwnerResolution result =
        policy.resolve(new OwnerResolutionContext(tenantId, customerId, overrideId));

    assertThat(result.ownerId()).isEqualTo(overrideId);
    assertThat(result.reason()).isEqualTo(OwnerResolutionReason.EXPLICIT_OVERRIDE);
  }

  @Test
  void activeAssignmentIsPrimaryAndHistoricalLadderStepsAreNotUsed() {
    UUID primaryId = UUID.randomUUID();
    when(assignmentService.findCurrent(tenantId, customerId))
        .thenReturn(Optional.of(assignment(primaryId)));
    when(userQueryService.findById(tenantId, primaryId))
        .thenReturn(Optional.of(activeUser(primaryId)));

    OwnerResolution result = policy.resolve(new OwnerResolutionContext(tenantId, customerId, null));

    assertThat(result.ownerId()).isEqualTo(primaryId);
    assertThat(result.reason()).isEqualTo(OwnerResolutionReason.PRIMARY_ASSIGNMENT);
  }

  @Test
  void inactiveAssignmentIsIgnoredAndRequiredModeNeedsTriage() {
    UUID inactiveId = UUID.randomUUID();
    when(assignmentService.findCurrent(tenantId, customerId))
        .thenReturn(Optional.of(assignment(inactiveId)));
    when(userQueryService.findById(tenantId, inactiveId))
        .thenReturn(Optional.of(UserDto.builder().id(inactiveId).isActive(false).build()));
    mode(OwnershipMode.REQUIRED);

    OwnerResolution result = policy.resolve(new OwnerResolutionContext(tenantId, customerId, null));

    assertThat(result.ownerId()).isNull();
    assertThat(result.reason()).isEqualTo(OwnerResolutionReason.TRIAGE_REQUIRED);
  }

  @Test
  void optionalModeAllowsUnassignedCustomer() {
    when(assignmentService.findCurrent(tenantId, customerId)).thenReturn(Optional.empty());
    mode(OwnershipMode.OPTIONAL);

    OwnerResolution result = policy.resolve(new OwnerResolutionContext(tenantId, customerId, null));

    assertThat(result.ownerId()).isNull();
    assertThat(result.reason()).isEqualTo(OwnerResolutionReason.OPTIONAL_UNASSIGNED);
  }

  @Test
  void exemptModeDoesNotRequireOwnership() {
    when(assignmentService.findCurrent(tenantId, customerId)).thenReturn(Optional.empty());
    mode(OwnershipMode.EXEMPT);

    OwnerResolution result = policy.resolve(new OwnerResolutionContext(tenantId, customerId, null));

    assertThat(result.ownerId()).isNull();
    assertThat(result.reason()).isEqualTo(OwnerResolutionReason.OWNERSHIP_EXEMPT);
  }

  private void mode(OwnershipMode mode) {
    when(ownershipPolicyService.requirePolicy(tenantId)).thenReturn(ownershipPolicy);
    when(ownershipPolicy.getDefaultMode()).thenReturn(mode);
  }

  private UserDto activeUser(UUID userId) {
    return UserDto.builder().id(userId).isActive(true).build();
  }

  private CustomerCommercialAssignment assignment(UUID representativeId) {
    return CustomerCommercialAssignment.open(
        customerId,
        representativeId,
        Instant.parse("2026-07-28T09:00:00Z"),
        AssignmentSource.MANUAL,
        ActorRef.user(UUID.randomUUID()),
        "OWNERSHIP_POLICY_V1",
        null);
  }
}
