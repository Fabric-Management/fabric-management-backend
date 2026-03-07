package com.fabricmanagement.production.quality.result.domain;

/**
 * Quality gate decision for a fiber test result.
 *
 * <pre>
 * PENDING ──approve()──▶ APPROVED
 *    │
 *    ├──reject()───────▶ REJECTED
 *    │
 *    └──conditionalAccept()─▶ CONDITIONAL_ACCEPT
 *
 * PENDING             : Test recorded, awaiting quality engineer review.
 * APPROVED            : Batch passes all specifications — clear for production.
 * REJECTED            : Batch fails critical thresholds — blocked from production.
 * CONDITIONAL_ACCEPT  : Batch has minor deviations but is usable with restrictions
 *                       (e.g. blending only, downgraded use, limited lot size).
 * </pre>
 */
public enum TestApprovalStatus {
  PENDING,
  APPROVED,
  REJECTED,
  CONDITIONAL_ACCEPT
}
