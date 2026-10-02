package com.fabricmanagement.product.fiber.dto;

/** Stable reason codes for a denied fibre action; clients translate the code. */
public enum FiberActionBlockedReason {
  FIBER_SHARED_READ_ONLY,
  FIBER_WRITE_PERMISSION_REQUIRED,
  FIBER_INACTIVE,
  FIBER_OBSOLETE,
  FIBER_MATERIAL_SOURCE_IMMUTABLE,
  FIBER_BLEND_MATERIAL_SOURCE_FORBIDDEN
}
