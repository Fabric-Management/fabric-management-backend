package com.fabricmanagement.platform.organization.dto;

import com.fabricmanagement.platform.communication.domain.ContactType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Atomic edit request for an organization–contact pair.
 *
 * <p>Combines contact point updates (value, type) with owner-link updates (label, isPersonal,
 * isDefault, department) into a single transactional operation.
 *
 * <p>All fields use patch semantics: {@code null} means "don't change".
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EditOrganizationContactRequest {

  private String contactValue;
  private ContactType contactType;
  private String label;
  private Boolean isPersonal;

  private Boolean isDefault;
  private String department;
}
