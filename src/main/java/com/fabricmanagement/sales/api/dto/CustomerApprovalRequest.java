package com.fabricmanagement.sales.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CustomerApprovalRequest {

  @NotBlank(message = "Token is required")
  private String token;

  // Additional data collection (client-side generated)
  private String ipAddress;
  private String userAgent;
}
