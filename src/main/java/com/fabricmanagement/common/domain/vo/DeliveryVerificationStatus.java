package com.fabricmanagement.common.domain.vo;

/**
 * Whether the details used to deliver goods (a partner record, an address card, an order delivery)
 * have been confirmed by someone who holds {@code sales:verify-delivery} (CONSIGNEE-1 C5).
 *
 * <p>Not the registry's identity check ({@code VerifiedStatus} of a trading partner registry): that
 * says who the company is, this says that the delivery details are right.
 */
public enum DeliveryVerificationStatus {
  /** Entered by someone who may not confirm it; waiting for a verifier. */
  UNVERIFIED,
  /** Confirmed by a verifier, or created by one. */
  VERIFIED;

  public boolean isVerified() {
    return this == VERIFIED;
  }
}
