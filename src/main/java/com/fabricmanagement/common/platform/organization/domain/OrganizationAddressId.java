package com.fabricmanagement.common.platform.organization.domain;

import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Composite key for OrganizationAddress junction entity. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationAddressId implements Serializable {

  private static final long serialVersionUID = 1L;

  private UUID organizationId;
  private UUID addressId;
}
