package com.fabricmanagement.flowboard.automation.domain;

/**
 * AutomationRule'u tetikleyen olay tipleri.
 *
 * <p>Docs: {@code 07-flowboard/smart-task-generator.md} — AutomationTriggerType
 */
public enum AutomationTriggerType {

  /**
   * Task statüsü değiştiğinde — triggerConfig: {"fromStatus": "IN_PROGRESS", "toStatus": "DONE"}
   */
  STATUS_CHANGED,

  /** Deadline'a yaklaşıldığında — triggerConfig: {"hoursBeforeDeadline": 24} */
  DEADLINE_APPROACHING,

  /** Task birine atandığında — triggerConfig: {"assigneeType": "ANY"} */
  TASK_ASSIGNED,

  /** Task uzun süre atanmamışken — triggerConfig: {"maxHoursUnassigned": 2} */
  TASK_UNASSIGNED_TOO_LONG,

  /** Etiket eklendiğinde — triggerConfig: {"labelName": "VIP_CLIENT"} */
  LABEL_ADDED,

  /** Etiket kaldırıldığında — triggerConfig: {"labelName": "URGENT"} */
  LABEL_REMOVED,

  /** Tüm checklist tamamlandığında */
  CHECKLIST_COMPLETED,

  /** Toplam süre tahminini aştığında — triggerConfig: {"thresholdPercent": 150} */
  TIMER_EXCEEDED,

  /** Kullanıcı WIP limiti aşıldığında */
  WIP_EXCEEDED,

  /** Priority değiştiğinde — triggerConfig: {"newPriority": "CRITICAL"} */
  PRIORITY_CHANGED
}
