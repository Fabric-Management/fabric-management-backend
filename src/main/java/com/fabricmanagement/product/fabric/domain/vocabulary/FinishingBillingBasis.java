package com.fabricmanagement.product.fabric.domain.vocabulary;

/**
 * Which metres a finishing price is multiplied by. Most subcontractors bill what comes back ({@link
 * #RETURNED}); a few bill what was sent in ({@link #SENT}). Which returned metres count, first
 * quality only or every grade, is a matter of which grades the firm's price rows cover.
 */
public enum FinishingBillingBasis {
  SENT,
  RETURNED
}
