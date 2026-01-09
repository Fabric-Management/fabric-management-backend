package com.fabricmanagement.common.platform.communication.domain;

import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** Composite primary key for AddressContact junction entity. */
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class AddressContactId implements Serializable {

  private UUID addressId;
  private UUID contactId;
}
