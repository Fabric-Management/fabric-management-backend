package com.fabricmanagement.product.fiber.domain;

/** What a tenant quality profile targets (FIBER-CATALOG-1). */
public enum FiberQualityTargetType {
  /** A shared ISO code: applies to a pure fibre of that ISO at exactly 100%, never to a blend. */
  ISO_CODE,
  /** One exact fibre (shared/own pure or own blend) with its captured composition. */
  FIBER
}
