package com.fabricmanagement.common.platform.communication.domain;

import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** Composite primary key for CompanyAddress junction entity. */
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class CompanyAddressId implements Serializable {

  private UUID companyId;
  private UUID addressId;
}
