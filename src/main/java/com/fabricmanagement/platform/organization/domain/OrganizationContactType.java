package com.fabricmanagement.platform.organization.domain;

/**
 * Classifies the role of a contact within an organization–contact junction.
 *
 * <p>Unlike {@link com.fabricmanagement.platform.communication.domain.ContactType} which describes
 * the <em>channel</em> (EMAIL, MOBILE, …), this enum describes the <em>purpose</em> of the contact
 * relative to the organization (billing, technical support, etc.).
 */
public enum OrganizationContactType {

  /** Main/default contact for general enquiries. */
  PRIMARY,

  /** Contact used for invoicing and financial correspondence. */
  BILLING,

  /** Contact for technical or IT-related issues. */
  TECHNICAL,

  /** Contact for customer/partner support requests. */
  SUPPORT
}
