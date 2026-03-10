package com.fabricmanagement.common.platform.organization.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Request to update mutable fields of an OrganizationContact junction (e.g. department). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateContactAssignmentRequest {

  /** New department label. Pass null or empty string to clear the department. */
  private String department;
}
