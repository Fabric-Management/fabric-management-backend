package com.fabricmanagement.sales.ownership.domain;

import java.io.Serializable;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** Composite identity for a customer-account-team membership. */
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class CustomerAccountTeamMemberId implements Serializable {
  private UUID customerId;
  private UUID userId;
}
