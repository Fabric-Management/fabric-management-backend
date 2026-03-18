package com.fabricmanagement.flowboard.task.domain;

/**
 * Task durum makinesi.
 *
 * <pre>
 * BACKLOG → TO_DO → IN_PROGRESS → IN_REVIEW → DONE
 *                 ↘ BLOCKED (herhangi bir yerden)
 *                 ↘ CANCELLED
 * BLOCKED → IN_PROGRESS
 * DONE → IN_PROGRESS (yeniden açılırsa)
 * </pre>
 */
public enum TaskStatus {
  BACKLOG,
  TODO,
  IN_PROGRESS,
  IN_REVIEW,
  DONE,
  BLOCKED,
  CANCELLED
}
