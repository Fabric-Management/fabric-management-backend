package com.fabricmanagement.platform.tradingpartner.dto;

import com.fabricmanagement.platform.tradingpartner.domain.PartnerLinkRole;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.Data;

/** Links an existing tenant partner to a customer in a role. */
@Data
public class CreatePartnerLinkRequest {

  @NotNull(message = "Linked partner is required")
  private UUID linkedPartnerId;

  @NotNull(message = "Link role is required")
  private PartnerLinkRole role;

  @Size(max = 500, message = "Note must not exceed 500 characters")
  private String note;
}
