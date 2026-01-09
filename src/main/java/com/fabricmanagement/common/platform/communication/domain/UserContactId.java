package com.fabricmanagement.common.platform.communication.domain;

import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** Composite primary key for UserContact junction entity. */
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class UserContactId implements Serializable {

  private UUID userId;
  private UUID contactId;
}
