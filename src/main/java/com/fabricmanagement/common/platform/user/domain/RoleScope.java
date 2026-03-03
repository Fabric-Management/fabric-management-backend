package com.fabricmanagement.common.platform.user.domain;

/**
 * Determines where a role is visible and assignable.
 *
 * <ul>
 *   <li>{@code INTERNAL} — Tenant's own employees (shown in employee creation form)
 *   <li>{@code PARTNER} — Trading partner users (shown when inviting partner users)
 *   <li>{@code SYSTEM} — Platform-level roles (never shown in tenant UI)
 * </ul>
 */
public enum RoleScope {
  INTERNAL,
  PARTNER,
  SYSTEM
}
