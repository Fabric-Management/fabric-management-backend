package com.fabricmanagement.common.infrastructure.security;

import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The finite permission vocabulary, not a user's effective grants.
 *
 * <p>PERM-CAT-1 is additive. Legacy frontend and unconsumed seed pairs remain until FE-ARCH-3
 * migrates their consumers and PERM-CAT-2 retires them. Where both Java and annotations consume a
 * pair, ANNOTATION takes precedence for bookkeeping; both call-site forms are guarded.
 *
 * <p>New production grants mirror fiber read/write only in production departments; costing mirrors
 * finance only in FINANCE; logistics delete mirrors cancel only. No other policy is widened.
 */
@Schema(
    name = "PermissionKey",
    enumAsRef = true,
    description = "Canonical resource:action pair. Catalogue membership does not grant access.")
public enum PermissionKey {
  ADMIN_ACCESS(
      "admin:access",
      EnforcedBy.FRONTEND_ROUTE,
      "Admin route uses role-based access. No default grant; do not change the admin access model."),
  COLORS_APPROVE("colors:approve", EnforcedBy.ANNOTATION, ""),
  COLORS_MANAGE("colors:manage", EnforcedBy.ANNOTATION, ""),
  COLORS_READ("colors:read", EnforcedBy.ANNOTATION, ""),
  COLORS_WRITE("colors:write", EnforcedBy.ANNOTATION, ""),
  COSTING_MANAGE(
      "costing:manage",
      EnforcedBy.ANNOTATION,
      "Mirrors finance:manage in FINANCE only (PERM-CAT-1)."),
  COSTING_READ(
      "costing:read",
      EnforcedBy.ANNOTATION,
      "Mirrors finance:read in FINANCE only, not department-agnostic finance grants (PERM-CAT-1)."),
  COSTING_WRITE(
      "costing:write",
      EnforcedBy.ANNOTATION,
      "Mirrors finance:write in FINANCE only (PERM-CAT-1)."),
  DASHBOARD_VIEW(
      "dashboard:view",
      EnforcedBy.FRONTEND_ROUTE,
      "Legacy frontend route gate; preserve until FE-ARCH-3 and PERM-CAT-2."),
  FIBER_APPROVE(
      "fiber:approve",
      EnforcedBy.NONE,
      "Retained existing seed; no current consumer found. Review in PERM-CAT-2."),
  FIBER_READ("fiber:read", EnforcedBy.ANNOTATION, ""),
  FIBER_WRITE("fiber:write", EnforcedBy.ANNOTATION, ""),
  FINANCE_MANAGE("finance:manage", EnforcedBy.ANNOTATION, ""),
  FINANCE_READ("finance:read", EnforcedBy.ANNOTATION, ""),
  FINANCE_WRITE("finance:write", EnforcedBy.ANNOTATION, ""),
  FLOWBOARD_EDIT(
      "flowboard:edit",
      EnforcedBy.NONE,
      "Retained existing seed; no current consumer found. Review in PERM-CAT-2."),
  FLOWBOARD_MANAGE(
      "flowboard:manage",
      EnforcedBy.FRONTEND_ROUTE,
      "Legacy frontend-only gate without a default grant; FE-ARCH-3 owns its resolution."),
  FLOWBOARD_READ("flowboard:read", EnforcedBy.ANNOTATION, ""),
  FLOWBOARD_VIEW(
      "flowboard:view",
      EnforcedBy.FRONTEND_ROUTE,
      "Legacy frontend route gate; preserve until FE-ARCH-3 and PERM-CAT-2."),
  FLOWBOARD_WRITE("flowboard:write", EnforcedBy.ANNOTATION, ""),
  LOGISTICS_CANCEL("logistics:cancel", EnforcedBy.ANNOTATION, ""),
  LOGISTICS_DELETE(
      "logistics:delete",
      EnforcedBy.ANNOTATION,
      "Mirrors logistics:cancel: MANAGER / WAREHOUSE / ORGANIZATION (PERM-CAT-1)."),
  LOGISTICS_DELIVER("logistics:deliver", EnforcedBy.ANNOTATION, ""),
  LOGISTICS_PREPARE("logistics:prepare", EnforcedBy.ANNOTATION, ""),
  LOGISTICS_READ("logistics:read", EnforcedBy.ANNOTATION, ""),
  LOGISTICS_SHIP("logistics:ship", EnforcedBy.ANNOTATION, ""),
  LOGISTICS_WRITE("logistics:write", EnforcedBy.ANNOTATION, ""),
  MEMBERS_MANAGE("members:manage", EnforcedBy.JAVA, ""),
  MEMBERS_READ("members:read", EnforcedBy.JAVA, ""),
  MEMBERS_WRITE("members:write", EnforcedBy.JAVA, ""),
  NOTIFICATIONS_VIEW(
      "notifications:view",
      EnforcedBy.FRONTEND_ROUTE,
      "Legacy frontend route gate; preserve until FE-ARCH-3 and PERM-CAT-2."),
  PARTNERS_READ(
      "partners:read",
      EnforcedBy.FRONTEND_ROUTE,
      "Legacy frontend route gate; preserve until FE-ARCH-3 and PERM-CAT-2."),
  PARTNERS_WRITE(
      "partners:write",
      EnforcedBy.NONE,
      "Retained existing seed; no current consumer found. Review in PERM-CAT-2."),
  PROCUREMENT_READ("procurement:read", EnforcedBy.ANNOTATION, ""),
  PROCUREMENT_WRITE("procurement:write", EnforcedBy.ANNOTATION, ""),
  PRODUCTION_READ(
      "production:read",
      EnforcedBy.ANNOTATION,
      "Mirrors fiber:read in the six production departments only (PERM-CAT-1)."),
  PRODUCTION_WRITE(
      "production:write",
      EnforcedBy.ANNOTATION,
      "Mirrors fiber:write in the six production departments only (PERM-CAT-1)."),
  PRODUCTS_READ("products:read", EnforcedBy.ANNOTATION, ""),
  PRODUCTS_WRITE("products:write", EnforcedBy.ANNOTATION, ""),
  PROJECTS_MANAGE(
      "projects:manage",
      EnforcedBy.NONE,
      "Retained existing seed; no current consumer found. Review in PERM-CAT-2."),
  PROJECTS_READ(
      "projects:read",
      EnforcedBy.NONE,
      "Retained existing seed; no current consumer found. Review in PERM-CAT-2."),
  PROJECTS_WRITE(
      "projects:write",
      EnforcedBy.NONE,
      "Retained existing seed; no current consumer found. Review in PERM-CAT-2."),
  QUALITY_APPROVE("quality:approve", EnforcedBy.ANNOTATION, ""),
  QUALITY_MANAGE("quality:manage", EnforcedBy.ANNOTATION, ""),
  QUALITY_READ("quality:read", EnforcedBy.ANNOTATION, ""),
  QUALITY_WRITE("quality:write", EnforcedBy.ANNOTATION, ""),
  REPORTS_EXPORT(
      "reports:export",
      EnforcedBy.NONE,
      "Retained existing seed; no current consumer found. Review in PERM-CAT-2."),
  REPORTS_VIEW(
      "reports:view",
      EnforcedBy.NONE,
      "Retained existing seed; no current consumer found. Review in PERM-CAT-2."),
  SALES_APPROVE("sales:approve", EnforcedBy.ANNOTATION, ""),
  SALES_ASSIGN_OWNER("sales:assign-owner", EnforcedBy.ANNOTATION, ""),
  SALES_CANCEL("sales:cancel", EnforcedBy.ANNOTATION, ""),
  SALES_CONFIRM("sales:confirm", EnforcedBy.ANNOTATION, ""),
  SALES_DELETE("sales:delete", EnforcedBy.ANNOTATION, ""),
  SALES_READ("sales:read", EnforcedBy.ANNOTATION, ""),
  SALES_SHIP("sales:ship", EnforcedBy.ANNOTATION, ""),
  SALES_WRITE("sales:write", EnforcedBy.ANNOTATION, ""),
  SETTINGS_MANAGE(
      "settings:manage",
      EnforcedBy.NONE,
      "Retained existing seed; no current consumer found. Review in PERM-CAT-2."),
  SETTINGS_READ("settings:read", EnforcedBy.ANNOTATION, ""),
  SETTINGS_VIEW(
      "settings:view",
      EnforcedBy.FRONTEND_ROUTE,
      "Legacy frontend route gate; preserve until FE-ARCH-3 and PERM-CAT-2."),
  SETTINGS_WRITE(
      "settings:write",
      EnforcedBy.NONE,
      "Retained existing seed; no current consumer found. Review in PERM-CAT-2."),
  YARN_READ("yarn:read", EnforcedBy.ANNOTATION, ""),
  YARN_WRITE("yarn:write", EnforcedBy.ANNOTATION, "");

  public enum EnforcedBy {
    ANNOTATION,
    JAVA,
    FRONTEND_ROUTE,
    NONE
  }

  private static final Map<String, PermissionKey> BY_KEY =
      Arrays.stream(values())
          .collect(Collectors.toUnmodifiableMap(PermissionKey::key, Function.identity()));

  private final String key;
  private final EnforcedBy enforcedBy;
  private final String note;

  PermissionKey(String key, EnforcedBy enforcedBy, String note) {
    this.key = key;
    this.enforcedBy = enforcedBy;
    this.note = note;
  }

  @JsonValue
  public String key() {
    return key;
  }

  public String resource() {
    return key.substring(0, key.indexOf(':'));
  }

  public String action() {
    return key.substring(key.indexOf(':') + 1);
  }

  public EnforcedBy enforcedBy() {
    return enforcedBy;
  }

  public boolean enforced() {
    return enforcedBy != EnforcedBy.NONE;
  }

  public String note() {
    return note;
  }

  /** Exact, case-sensitive pair lookup. Unknown, blank and malformed halves are rejected. */
  public static Optional<PermissionKey> of(String resource, String action) {
    if (resource == null || action == null || resource.contains(":") || action.contains(":")) {
      return Optional.empty();
    }
    return Optional.ofNullable(BY_KEY.get(resource + ":" + action));
  }
}
