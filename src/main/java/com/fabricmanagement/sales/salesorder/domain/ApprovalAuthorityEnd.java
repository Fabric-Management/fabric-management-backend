package com.fabricmanagement.sales.salesorder.domain;

/**
 * Why an approval authority ended (ADR-0014 OD-13). Every ending is permanent: the authority is
 * never in force again, whatever happens to the contact point afterwards; a new grant is a new
 * authority.
 */
public enum ApprovalAuthorityEnd {
  /** An authorised user revoked it, with a reason. */
  REVOKED,
  /**
   * The representative's contact point was changed to another address (or type) or deleted. Even if
   * it is changed back later, the authority stays ended.
   */
  CONTACT_ADDRESS_CHANGED,
  /** The contact point was removed from the customer's card. */
  CONTACT_REMOVED;

  /** Whether the authority ended because its address no longer reached the representative. */
  public boolean isContactChange() {
    return this != REVOKED;
  }
}
