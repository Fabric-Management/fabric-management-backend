package com.fabricmanagement.common.platform.organization.domain;

import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Composite key for OrganizationContact junction entity. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationContactId implements Serializable {

  private static final long serialVersionUID = 1L;

  private UUID organizationId;
  private UUID contactId;
}
