package com.fabricmanagement.approval.dto;

import com.fabricmanagement.approval.domain.ApprovalEntityType;
import com.fabricmanagement.approval.domain.ApproverRole;
import com.fabricmanagement.approval.domain.PolicyTargetLevel;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreatePolicyRequest {

  @NotNull private ApprovalEntityType entityType;

  @NotNull private PolicyTargetLevel requiredLevel;

  @NotNull private ApproverRole approverRole;

  @Min(1)
  private int promotionThreshold = 10;

  @Min(1)
  @Max(720)
  private int expiryHours = 48;
}
