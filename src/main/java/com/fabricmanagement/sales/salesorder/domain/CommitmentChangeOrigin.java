package com.fabricmanagement.sales.salesorder.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Who asked for a commitment change. A date the buyer moved (more quantity, a later need) is not a
 * promise the seller failed to keep, so the two are measured apart.
 */
@Schema(name = "CommitmentChangeOrigin", enumAsRef = true)
public enum CommitmentChangeOrigin {
  /** The first promise given for the order. */
  INITIAL,
  /** The buyer asked for the change. */
  BUYER_REQUEST,
  /** The seller revised the promise. */
  SELLER_REVISION
}
