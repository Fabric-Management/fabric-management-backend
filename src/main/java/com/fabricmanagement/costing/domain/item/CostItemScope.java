package com.fabricmanagement.costing.domain.item;

/** Scope of a cost item — system-wide or module-specific. */
public enum CostItemScope {
  /** Applies to all modules (e.g. RAW_PRODUCT, LABOR). */
  GLOBAL,

  /** Applies only to a specific module type (e.g. FIBER_BALING → FIBER). */
  MODULE_SPECIFIC
}
