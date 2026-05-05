package com.fabricmanagement.approval.domain;

/**
 * Onay mekanizmasına tabi olan entity türleri. Yeni bir modül onay akışına dahil edilecekse bu
 * enum'a eklenir. Veritabanında {@code VARCHAR(50)} olarak saklanır.
 */
public enum ApprovalEntityType {
  WORK_ORDER,
  RECIPE_CREATE,
  CRITICAL_ACTION,
  PURCHASE_ORDER
}
