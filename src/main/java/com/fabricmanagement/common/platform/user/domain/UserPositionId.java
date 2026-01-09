package com.fabricmanagement.common.platform.user.domain;

import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** Composite primary key for UserPosition junction table. */
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class UserPositionId implements Serializable {

  private UUID userId;
  private UUID positionId;
}
