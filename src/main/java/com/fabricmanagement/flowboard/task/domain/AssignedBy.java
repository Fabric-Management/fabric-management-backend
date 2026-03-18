package com.fabricmanagement.flowboard.task.domain;

/** Task atama kaynağını belirtir. */
public enum AssignedBy {
  /** Sistem tarafından otomatik atandı (taskType + moduleType → departman → personel). */
  SYSTEM,
  /** Manager tarafından havuzdan seçildi. */
  MANAGER,
  /** Personel kendisi aldı (SELF-assign). */
  SELF
}
