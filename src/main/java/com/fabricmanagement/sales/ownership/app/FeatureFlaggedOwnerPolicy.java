package com.fabricmanagement.sales.ownership.app;

import com.fabricmanagement.sales.ownership.domain.DefaultOwnerPolicy;
import com.fabricmanagement.sales.ownership.domain.OwnerResolution;
import com.fabricmanagement.sales.ownership.domain.OwnerResolutionContext;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
@RequiredArgsConstructor
public class FeatureFlaggedOwnerPolicy implements DefaultOwnerPolicy {

  private final AcquirerFirstPolicy acquirerFirstPolicy;
  private final AssignmentFirstPolicy assignmentFirstPolicy;
  private final OwnershipPolicyService ownershipPolicyService;

  @Value("${feature.sales.assignment-ladder-enabled:false}")
  private boolean globalAssignmentLadderEnabled;

  @Override
  public OwnerResolution resolve(OwnerResolutionContext context) {
    if (!globalAssignmentLadderEnabled) {
      return acquirerFirstPolicy.resolve(context);
    }
    boolean tenantEnabled =
        ownershipPolicyService.requirePolicy(context.tenantId()).isAssignmentLadderEnabled();
    return tenantEnabled
        ? assignmentFirstPolicy.resolve(context)
        : acquirerFirstPolicy.resolve(context);
  }
}
