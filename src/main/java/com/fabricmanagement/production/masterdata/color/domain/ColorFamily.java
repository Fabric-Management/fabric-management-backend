package com.fabricmanagement.production.masterdata.color.domain;

/**
 * Coarse hue grouping, used as a filter axis once a catalogue grows past a few dozen cards.
 *
 * <p>Deliberately a closed enum: colour families are universal. Tenant-specific groupings are a
 * different concept (tags), not a family.
 */
public enum ColorFamily {
  RED,
  ORANGE,
  YELLOW,
  GREEN,
  BLUE,
  PURPLE,
  PINK,
  BROWN,
  BEIGE,
  GREY,
  BLACK,
  WHITE,
  /** Prints and multi-colour cards. */
  MULTI,
  /** Undyed or not yet classified. */
  UNDEFINED
}
