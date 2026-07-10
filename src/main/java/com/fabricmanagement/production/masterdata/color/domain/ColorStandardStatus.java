package com.fabricmanagement.production.masterdata.color.domain;

/**
 * Internal sign-off state of the card's own shade standard.
 *
 * <p>Not a customer lab-dip approval — that is per customer, per substrate, per submission and
 * belongs to its own entity.
 */
public enum ColorStandardStatus {
  DRAFT,
  APPROVED
}
