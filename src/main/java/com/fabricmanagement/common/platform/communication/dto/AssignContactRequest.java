package com.fabricmanagement.common.platform.communication.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignContactRequest {

  @NotNull(message = "Contact ID is required")
  private UUID contactId;

  @Builder.Default private Boolean isDefault = false;

  /** Optional department label for this contact assignment (e.g. "Sales", "Support"). */
  private String department;
}
