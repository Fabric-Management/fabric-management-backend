package com.fabricmanagement.production.masterdata.color.domain;

/**
 * The Pantone carrier a code refers to. The same number on a different carrier is a different
 * physical reference, so the system must be stored alongside the code.
 */
public enum PantoneSystem {
  /** Textile Cotton eXtended — cotton swatch. The usual choice for fabric. */
  TCX,
  /** Textile Paper Green — paper swatch; successor to TPX. */
  TPG,
  /** Textile Paper eXtended — legacy paper swatch. */
  TPX,
  /** Nylon brights. */
  TN,
  /** Polyester. */
  TSX
}
