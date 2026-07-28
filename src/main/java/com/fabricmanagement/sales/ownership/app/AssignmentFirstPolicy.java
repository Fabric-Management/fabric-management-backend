package com.fabricmanagement.sales.ownership.app;

import com.fabricmanagement.platform.user.app.UserQueryService;
import com.fabricmanagement.platform.user.dto.UserDto;
import com.fabricmanagement.sales.ownership.domain.CustomerCommercialAssignment;
import com.fabricmanagement.sales.ownership.domain.DefaultOwnerPolicy;
import com.fabricmanagement.sales.ownership.domain.OwnerResolution;
import com.fabricmanagement.sales.ownership.domain.OwnerResolutionContext;
import com.fabricmanagement.sales.ownership.domain.OwnerResolutionReason;
import com.fabricmanagement.sales.ownership.domain.OwnershipMode;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AssignmentFirstPolicy implements DefaultOwnerPolicy {

  private final CustomerEligibilityService customerEligibilityService;
  private final CustomerCommercialAssignmentService assignmentService;
  private final OwnershipPolicyService ownershipPolicyService;
  private final UserQueryService userQueryService;
  private final AcquirerFirstPolicy acquirerFirstPolicy;

  @Override
  @Transactional(readOnly = true)
  public OwnerResolution resolve(OwnerResolutionContext context) {
    customerEligibilityService.requireEligible(context.tenantId(), context.customerId());
    Optional<CustomerCommercialAssignment> eligibleAssignment =
        assignmentService
            .findCurrent(context.tenantId(), context.customerId())
            .filter(
                assignment -> isActiveUser(context.tenantId(), assignment.getRepresentativeId()));

    if (context.requestedOwnerId() != null) {
      if (eligibleAssignment
          .map(CustomerCommercialAssignment::getRepresentativeId)
          .filter(context.requestedOwnerId()::equals)
          .isEmpty()) {
        acquirerFirstPolicy.resolve(context);
      }
      return new OwnerResolution(
          context.requestedOwnerId(), OwnerResolutionReason.EXPLICIT_OVERRIDE);
    }

    if (eligibleAssignment.isPresent()) {
      return new OwnerResolution(
          eligibleAssignment.orElseThrow().getRepresentativeId(),
          OwnerResolutionReason.PRIMARY_ASSIGNMENT);
    }

    OwnershipMode mode = ownershipPolicyService.requirePolicy(context.tenantId()).getDefaultMode();
    return switch (mode) {
      case REQUIRED -> new OwnerResolution(null, OwnerResolutionReason.TRIAGE_REQUIRED);
      case OPTIONAL -> new OwnerResolution(null, OwnerResolutionReason.OPTIONAL_UNASSIGNED);
      case EXEMPT -> new OwnerResolution(null, OwnerResolutionReason.OWNERSHIP_EXEMPT);
    };
  }

  private boolean isActiveUser(UUID tenantId, UUID userId) {
    return userQueryService
        .findById(tenantId, userId)
        .map(UserDto::getIsActive)
        .map(Boolean.TRUE::equals)
        .orElse(false);
  }
}
