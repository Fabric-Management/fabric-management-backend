package com.fabricmanagement.sales.salesorder.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/** How the buyer's agreement to a commitment reached the seller. */
@Schema(name = "CommitmentChannel", enumAsRef = true)
public enum CommitmentChannel {
  PHONE,
  EMAIL,
  MESSAGE,
  IN_PERSON,
  DOCUMENT,
  /** The customer approved the sent order version through the e-mailed link and one-time code. */
  APPROVAL_LINK,
  /** The customer approved the sent order version from their own customer account. */
  CUSTOMER_ACCOUNT
}
