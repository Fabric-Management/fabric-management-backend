package com.fabricmanagement.sales.quote.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SendQuoteRequest {

  @NotBlank(message = "Customer email is required")
  @Email(message = "Customer email must be valid")
  private String customerEmail;
}
