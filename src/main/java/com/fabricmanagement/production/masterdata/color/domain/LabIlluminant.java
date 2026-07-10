package com.fabricmanagement.production.masterdata.color.domain;

/** Light source a Lab measurement was taken under. Lab values are not comparable across these. */
public enum LabIlluminant {
  /** Average daylight, 6504 K. The usual textile reference. */
  D65,
  /** Horizon daylight, 5003 K. */
  D50,
  /** Incandescent, 2856 K. */
  A,
  /** Narrow-band fluorescent, 4000 K. */
  F11,
  /** Store lighting, 4000 K. */
  TL84
}
