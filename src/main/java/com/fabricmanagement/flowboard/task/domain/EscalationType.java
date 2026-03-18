package com.fabricmanagement.flowboard.task.domain;

/** Eskalasyonun nedeni/kategorisi. */
public enum EscalationType {
  DEADLINE_PASSED,
  UNASSIGNED,
  BLOCKED_TOO_LONG,
  UNTOUCHED,
  TIME_EXCEEDED,
  QUALITY_ISSUE
}
