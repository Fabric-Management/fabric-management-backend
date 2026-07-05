package com.fabricmanagement.platform.tradingpartner.dto;

import com.fabricmanagement.platform.tradingpartner.domain.PartnerContactRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreatePartnerContactRequest {

  @NotBlank(message = "Contact name is required")
  @Size(max = 120, message = "Contact name must not exceed 120 characters")
  private String name;

  @Email(message = "Contact email must be valid")
  @Size(max = 254, message = "Contact email must not exceed 254 characters")
  private String email;

  @Size(max = 30, message = "Contact phone must not exceed 30 characters")
  private String phone;

  @NotNull(message = "Contact role is required")
  private PartnerContactRole role;

  private Boolean whatsappEnabled = false;

  private Boolean primary = false;
}
