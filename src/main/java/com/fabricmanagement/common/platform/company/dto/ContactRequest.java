package com.fabricmanagement.common.platform.company.dto;

import com.fabricmanagement.common.platform.communication.domain.ContactType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Nested DTO for adding a contact when creating a company (createWithContact). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactRequest {

  @NotBlank(message = "Contact value is required")
  private String contactValue;

  @NotNull(message = "Contact type is required")
  private ContactType contactType;

  private Boolean isDefault;
  private String department;
}
