package com.fabricmanagement.inventory.lothealth.domain;

/**
 * How fast a product sells (Fatih, 2026-10-14). The slower it sells, the more pieces a lot must
 * keep to stay sellable, and the more a lot-closing option may exceed the tolerance. Until speed is
 * measured, every product is {@link #MOVING}.
 */
public enum ProductSpeed {
  /** Sells as before; the standard rules apply. */
  MOVING,
  /** Sells clearly less than before, or the stock lasts long. */
  SLOWING,
  /** Hardly sells any more; the stock should be used up as soon as healthily possible. */
  STALLED
}
