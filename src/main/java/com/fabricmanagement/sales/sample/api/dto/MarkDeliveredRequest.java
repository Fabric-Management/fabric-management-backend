package com.fabricmanagement.sales.sample.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MarkDeliveredRequest {

  @NotBlank(message = "Recipient name is required")
  private String recipientName;

  /** Optional URL of the delivery proof photo. */
  private String photoUrl;
}
