package com.fabricmanagement.production.masterdata.fiber.domain;

import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** Composite primary key for FiberCertificationLink junction entity. */
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class FiberCertificationLinkId implements Serializable {

  private UUID fiberId;
  private UUID certificationId;
}
