package com.fabricmanagement.product.yarn.domain.vocabulary;

/**
 * Independent, multi-valued yarn construction features.
 *
 * <p>This is deliberately separate from spinning technology and filament form: one yarn can be
 * RING, CORE_SPUN and SLUB at the same time. The v1 set is closed; adding a member is a release,
 * not tenant data.
 */
public enum YarnConstructionFeature {
  CORE_SPUN,
  SIRO,
  SLUB,
  COVERED
}
