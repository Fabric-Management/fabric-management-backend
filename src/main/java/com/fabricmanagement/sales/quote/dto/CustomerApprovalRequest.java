package com.fabricmanagement.sales.quote.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CustomerApprovalRequest {

  @NotBlank(message = "Token is required")
  private String token;

  // Additional data collection (client-side generated)
  @Size(max = 45, message = "IP address must be 45 characters or less")
  private String ipAddress;

  @Size(max = 512, message = "User agent must be 512 characters or less")
  private String userAgent;

  @Size(max = 2000, message = "Customer note must be 2000 characters or less")
  private String customerNote;
}
