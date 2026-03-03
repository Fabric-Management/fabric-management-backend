package com.fabricmanagement.common.platform.user.domain;

/**
 * Determines whether a user is the tenant's own staff or an external contact.
 *
 * <ul>
 *   <li>{@code INTERNAL} — Tenant's own employees. May have an Employee (HR) record.
 *   <li>{@code EXTERNAL} — Partner, supplier, or customer users. No HR records.
 * </ul>
 */
public enum UserType {
  INTERNAL,
  EXTERNAL
}
