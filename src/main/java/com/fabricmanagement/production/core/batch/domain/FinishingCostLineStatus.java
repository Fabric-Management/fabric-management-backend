package com.fabricmanagement.production.core.batch.domain;

/** How one line of a finishing cost suggestion came out. */
public enum FinishingCostLineStatus {
  /** A price applies and the amount is metres times that price; a price of zero is a free grade. */
  PRICED,
  /** The firm prices other grades but not this one, so this grade is not billed. */
  NOT_BILLED,
  /** The price is per kilogram and the lot has no weight, so no amount can be worked out. */
  NOT_COMPUTABLE,
  /** Nothing prices this work, or the price needs the metres split by grade first. */
  NO_PRICE
}
