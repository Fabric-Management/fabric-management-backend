package com.fabricmanagement.platform.tradingpartner.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Data;

@Data
public class QuickCreateCustomerRequest {

  @NotBlank(message = "Company name is required")
  @Size(max = 255, message = "Company name must not exceed 255 characters")
  private String companyName;

  @Size(max = 500, message = "Address must not exceed 500 characters")
  private String address;

  @Size(max = 30, message = "Phone must not exceed 30 characters")
  private String phone;

  @Size(max = 50, message = "Tax number must not exceed 50 characters")
  private String taxNumber;

  @Valid
  @NotEmpty(message = "At least one contact is required")
  private List<QuickCreateCustomerContactRequest> contacts;
}
