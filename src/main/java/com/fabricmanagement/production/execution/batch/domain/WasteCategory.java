package com.fabricmanagement.production.execution.batch.domain;

/**
 * Category of production waste (fire/telef) for reporting and analysis.
 *
 * <p>Used when recording waste via {@code recordWaste} to indicate why the waste occurred.
 */
public enum WasteCategory {
  /** Normal production loss (e.g. standard waste rate). */
  NORMAL_PRODUCTION,

  /** Waste due to machine fault or breakdown. */
  MACHINE_FAULT,

  /** Waste due to raw material defect. */
  RAW_MATERIAL_DEFECT,

  /** Waste due to handling or transport damage. */
  HANDLING_DAMAGE,

  /** Other / unspecified. */
  OTHER
}
