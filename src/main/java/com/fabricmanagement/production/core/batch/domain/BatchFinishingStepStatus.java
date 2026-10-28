package com.fabricmanagement.production.core.batch.domain;

/** Where a finishing step stands: the lot is out, back, or the entry was withdrawn. */
public enum BatchFinishingStepStatus {
  /** The lot is out for the process; it stays out of sale until it returns. */
  SENT,
  /** The process is done and the lot is back, or a finished history was entered for it. */
  DONE,
  /** Entered by mistake while the lot was still out; kept so the history explains itself. */
  CANCELLED
}
