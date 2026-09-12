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
 * <p>PERM-CAT-2 retired unused pairs with db/migration/
 * V20260912120000__retire_stale_permission_pairs.sql. Where both Java and annotations consume a
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
  FIBER_READ("fiber:read", EnforcedBy.ANNOTATION, ""),
  FIBER_WRITE("fiber:write", EnforcedBy.ANNOTATION, ""),
  FINANCE_MANAGE("finance:manage", EnforcedBy.ANNOTATION, ""),
  FINANCE_READ("finance:read", EnforcedBy.ANNOTATION, ""),
  FINANCE_WRITE("finance:write", EnforcedBy.ANNOTATION, ""),
  FLOWBOARD_READ("flowboard:read", EnforcedBy.ANNOTATION, ""),
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
  QUALITY_APPROVE("quality:approve", EnforcedBy.ANNOTATION, ""),
  QUALITY_MANAGE("quality:manage", EnforcedBy.ANNOTATION, ""),
  QUALITY_READ("quality:read", EnforcedBy.ANNOTATION, ""),
  QUALITY_WRITE("quality:write", EnforcedBy.ANNOTATION, ""),
  SALES_APPROVE("sales:approve", EnforcedBy.ANNOTATION, ""),
  SALES_ASSIGN_OWNER("sales:assign-owner", EnforcedBy.ANNOTATION, ""),
  SALES_CANCEL("sales:cancel", EnforcedBy.ANNOTATION, ""),
  SALES_CONFIRM("sales:confirm", EnforcedBy.ANNOTATION, ""),
  SALES_DELETE("sales:delete", EnforcedBy.ANNOTATION, ""),
  SALES_READ("sales:read", EnforcedBy.ANNOTATION, ""),
  SALES_SHIP("sales:ship", EnforcedBy.ANNOTATION, ""),
  SALES_WRITE("sales:write", EnforcedBy.ANNOTATION, ""),
  SETTINGS_READ("settings:read", EnforcedBy.ANNOTATION, ""),
  YARN_READ("yarn:read", EnforcedBy.ANNOTATION, ""),
  YARN_WRITE("yarn:write", EnforcedBy.ANNOTATION, "");

  public enum EnforcedBy {
    ANNOTATION,
    JAVA
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
