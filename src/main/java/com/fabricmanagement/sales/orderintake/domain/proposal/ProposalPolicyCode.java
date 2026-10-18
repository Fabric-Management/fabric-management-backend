package com.fabricmanagement.sales.orderintake.domain.proposal;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "StockProposalPolicyCode", enumAsRef = true)
public enum ProposalPolicyCode {
  STANDARD,
  FABRIC_ROLL_BASE,
  /** Stored evaluation predates explicit policy evidence. */
  UNKNOWN
}
