package com.fabricmanagement.sales.salesorder.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "OrderWorkEventType", enumAsRef = true)
public enum OrderWorkEventType {
  /** The work was put in a team's queue, unassigned. */
  ROUTED,
  /** A team member took the work explicitly. */
  CLAIMED,
  /** An authorised person assigned or reassigned it, with a reason. */
  ASSIGNED,
  /** The responsible person or an authorised person gave it back to the queue, with a reason. */
  RELEASED
}
