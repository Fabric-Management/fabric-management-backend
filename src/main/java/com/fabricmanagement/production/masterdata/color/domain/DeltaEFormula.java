package com.fabricmanagement.production.masterdata.color.domain;

/**
 * Colour-difference formula a tolerance is expressed in. A bare tolerance number is meaningless
 * without it: the same pair of colours yields different values under each formula.
 */
public enum DeltaEFormula {
  /** Plain Euclidean distance in Lab. Simple, poorly correlated with perception. */
  CIE76,
  CIE94,
  /** Modern perceptually-uniform formula. */
  CIEDE2000,
  /** CMC lightness:chroma 2:1 — long-standing textile default. */
  CMC_2_1
}
