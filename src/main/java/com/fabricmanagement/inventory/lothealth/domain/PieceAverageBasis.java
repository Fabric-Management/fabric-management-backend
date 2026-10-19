package com.fabricmanagement.inventory.lothealth.domain;

/** Measurement evidence behind a lot's average piece size; independent of API frameworks. */
public enum PieceAverageBasis {
  INITIAL_MEASUREMENTS,
  CURRENT_MEASUREMENTS,
  UNKNOWN
}
