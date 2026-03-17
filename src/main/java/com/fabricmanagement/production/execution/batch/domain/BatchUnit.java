package com.fabricmanagement.production.execution.batch.domain;

/**
 * Physical unit of measurement for batch quantities.
 *
 * <p>Ref: batch-production.md — unit: Enum KG / MT / PIECE
 */
public enum BatchUnit {
  /** Kilogram — used for fiber, yarn, chemical batches. */
  KG,
  /** Metric Ton — used for large bulk batches. */
  MT,
  /** Piece — used for discrete item batches (e.g. fabric rolls). */
  PIECE;
}
