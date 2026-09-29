package com.fabricmanagement.product.fiber.domain;

/** Structural kind of a fibre definition. */
public enum FiberKind {
  /** One material with one shared ISO code; stored composition is empty. */
  PURE,
  /** Two or more pure fibres with percentages; carries no ISO code of its own. */
  BLEND
}
