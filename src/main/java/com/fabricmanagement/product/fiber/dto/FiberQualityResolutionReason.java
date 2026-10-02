package com.fabricmanagement.product.fiber.dto;

/** How the default quality profile for an exact fibre and effective composition was resolved. */
public enum FiberQualityResolutionReason {
  /** An active default FIBER profile for this exact fibre and composition. */
  EXACT_FIBER_DEFAULT,
  /** A pure fibre at exactly 100%: the tenant's active default for its shared ISO code. */
  ISO_DEFAULT,
  /** Nothing applies; the batch stays in the pending/manual-review path. */
  NO_APPLICABLE_DEFAULT
}
