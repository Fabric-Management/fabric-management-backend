package com.fabricmanagement.flowboard.automation.domain;

/**
 * AutomationRule aksiyonları.
 *
 * <p>Docs: {@code 07-flowboard/smart-task-generator.md} — AutomationActionType
 */
public enum AutomationActionType {

  /** Task statüsünü değiştir — actionConfig: {"newStatus": "IN_REVIEW"} */
  CHANGE_STATUS,

  /** Belirli kişiye ata — actionConfig: {"userId": "...", "assignedBy": "SYSTEM"} */
  ASSIGN_USER,

  /** Departman bazlı ata — actionConfig: {"departmentId": "...", "role": "DEPARTMENT_ADMIN"} */
  ASSIGN_DEPARTMENT,

  /** Belirli kişiye bildirim — actionConfig: {"userId": "...", "message": "..."} */
  NOTIFY_USER,

  /** Manager'a bildirim — actionConfig: {"message": "WIP limiti aşıldı!"} */
  NOTIFY_MANAGER,

  /** Yeni task oluştur — actionConfig: {"taskType": "WAREHOUSE", "titleTemplate": "..."} */
  CREATE_TASK,

  /** Etiket ekle — actionConfig: {"labelName": "URGENT"} */
  ADD_LABEL,

  /** Etiket kaldır — actionConfig: {"labelName": "WAITING_EXTERNAL"} */
  REMOVE_LABEL,

  /** Priority score güncelle — actionConfig: {"priorityBonus": 20} */
  UPDATE_PRIORITY,

  /** Eskalasyon tetikle — actionConfig: {"escalateTo": "DEPARTMENT_ADMIN"} */
  ESCALATE
}
