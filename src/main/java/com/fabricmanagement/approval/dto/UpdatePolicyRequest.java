package com.fabricmanagement.approval.dto;

import com.fabricmanagement.approval.domain.ApproverRole;
import com.fabricmanagement.approval.domain.PolicyTargetLevel;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdatePolicyRequest {

  @NotNull private PolicyTargetLevel requiredLevel;

  @NotNull private ApproverRole approverRole;

  @Min(1)
  private int promotionThreshold;
}
