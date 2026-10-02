package com.fabricmanagement.sales.salesorder.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * How the customer's representative reached the decision. Both doors lead to the same approval of
 * the same version: an approval through one is the approval seen through the other.
 */
@Schema(name = "CustomerApprovalChannel", enumAsRef = true)
public enum CustomerApprovalChannel {
  /** The link e-mailed to the order's contact, verified with a one-time code. */
  EMAIL_LINK,
  /** The representative's own customer account, with the authority to approve the order. */
  CUSTOMER_ACCOUNT
}
