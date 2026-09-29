package com.fabricmanagement.product.fiber.domain;

/** Who owns a fibre definition, as seen by the reading tenant. */
public enum FiberCatalogScope {
  /** Platform catalogue record, identical for every tenant and read-only for tenants. */
  SHARED,
  /** Record owned by the reading tenant. */
  TENANT
}
