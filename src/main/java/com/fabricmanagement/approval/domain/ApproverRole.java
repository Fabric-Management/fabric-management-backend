package com.fabricmanagement.approval.domain;

/**
 * Onay politikasında onayı verecek rol. Magic string'ler yerine tip güvenliği sağlar.
 *
 * <p>Yeni roller eklenmek istendiğinde buraya eklenmesi yeterlidir.
 */
public enum ApproverRole {
  TENANT_ADMIN,
  DEPARTMENT_MANAGER,
  MANAGER,
  HR,
  CEO,
  QUALITY_MANAGER
}
