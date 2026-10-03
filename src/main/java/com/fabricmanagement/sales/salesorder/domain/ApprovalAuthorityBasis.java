package com.fabricmanagement.sales.salesorder.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/** What an approval authority rests on (ADR-0014 D4, OD-3c). */
@Schema(name = "ApprovalAuthorityBasis", enumAsRef = true)
public enum ApprovalAuthorityBasis {
  /** A written mandate or letter from the customer naming the person. */
  WRITTEN_MANDATE,
  /** The customer's management confirmed it in correspondence. */
  CUSTOMER_CORRESPONDENCE,
  /** A framework agreement or contract clause names the person or role. */
  CONTRACT_CLAUSE,
  /** A public company register lists the person as authorised to represent the company. */
  COMPANY_REGISTER,
  OTHER
}
