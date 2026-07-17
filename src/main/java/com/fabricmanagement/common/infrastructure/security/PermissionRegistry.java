package com.fabricmanagement.common.infrastructure.security;

import java.util.Set;

/**
 * Defines the valid resources and actions within the system for permission management. Acts as the
 * backend counterpart to frontend routing rules.
 */
public final class PermissionRegistry {

  private PermissionRegistry() {
    // Utility class
  }

  public static final Set<String> VALID_RESOURCES =
      Set.of(
          "dashboard",
          "sales",
          "fiber",
          "projects",
          "products",
          "colors",
          "partners",
          "procurement",
          "flowboard",
          "settings",
          "admin",
          "reports",
          "notifications",
          "quality",
          "members",
          "logistics");

  public static final Set<String> VALID_ACTIONS =
      Set.of(
          "view", "read", "write", "manage", "access", "delete", "approve", "export", "confirm",
          "ship", "cancel", "prepare", "deliver");

  public static boolean isValidResource(String resource) {
    return resource != null && VALID_RESOURCES.contains(resource);
  }

  public static boolean isValidAction(String action) {
    return action != null && VALID_ACTIONS.contains(action);
  }
}
