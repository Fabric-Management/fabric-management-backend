package com.fabricmanagement.production.quality.result.dto;

import com.fabricmanagement.production.quality.result.domain.TestApprovalStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateApprovalRequest {

  private Long version;

  @NotNull(message = "Approval status is required")
  private TestApprovalStatus approvalStatus;

  private String remarks;
}
