package com.fabricmanagement.common.infrastructure.security;

import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;

/**
 * Abstract base for all module-level access services (CR-12-04). Centralizes:
 *
 * <ul>
 *   <li>{@code hasPermission()} — null/unknown checks, context extraction, logging
 *   <li>{@code hasManagerPermission()} — admin/manager gate (CR-12-06)
 *   <li>{@code extractContext()} / {@code fallbackRoleOnlyCheck()} — shared helpers
 * </ul>
 *
 * <p>Subclasses only need to provide:
 *
 * <ul>
 *   <li>{@link #knownModules()} — valid module identifiers
 *   <li>{@link #writeDepartmentsByModule()} — module → write-authorized departments
 *   <li>{@link #evaluateRead(AuthenticatedUserContext, String)} — module-specific read rules
 *   <li>{@link #evaluateWrite(AuthenticatedUserContext, String)} — module-specific write rules
 * </ul>
 *
 * <p><b>Security Note (CR-12-08):</b> {@code fallbackRoleOnlyCheck} is a safety net for test
 * contexts or legacy paths where {@link AuthenticatedUserContext} is not populated. It
 * intentionally relaxes department checks. In production, JWT filter must always populate the
 * context. Any fallback usage is logged at WARN level for monitoring.
 */
@Slf4j
public abstract class BaseAccessService {

  public static final String ACTION_READ = "READ";
  public static final String ACTION_WRITE = "WRITE";

  protected static final Set<String> SUPER_ROLES = Set.of("ADMIN", "PLATFORM_ADMIN");
  protected static final Set<String> MANAGEMENT_ROLES =
      Set.of("ADMIN", "PLATFORM_ADMIN", "MANAGER");
  protected static final Set<String> OPERATIONAL_ROLES =
      Set.of("ADMIN", "PLATFORM_ADMIN", "MANAGER", "SUPERVISOR");

  // ── Abstract hooks ─────────────────────────────────────────────────────────

  /** Return all known module identifiers (e.g. "FIBER", "INVOICE"). */
  protected abstract Set<String> knownModules();

  /** Return module → write-authorized department set mapping. */
  protected abstract Map<String, Set<String>> writeDepartmentsByModule();

  /**
   * Evaluate read access for a specific module. Called after super-role check has already returned
   * true for ADMINs. Default implementation: MANAGER/SUPERVISOR → always true, others → department
   * check.
   */
  protected abstract boolean evaluateRead(AuthenticatedUserContext ctx, String module);

  /**
   * Evaluate write access for a specific module. Called after super-role check. Default: look up
   * departments from {@link #writeDepartmentsByModule()}.
   */
  protected abstract boolean evaluateWrite(AuthenticatedUserContext ctx, String module);

  // ── Public API (SpEL entry point) ──────────────────────────────────────────

  public boolean hasPermission(Authentication authentication, String module, String action) {
    if (authentication == null || !authentication.isAuthenticated()) {
      log.debug("[{}] hasPermission denied: no valid authentication", serviceName());
      return false;
    }
    if (module == null || action == null) {
      log.warn(
          "[{}] hasPermission denied: module or action is null (module={}, action={})",
          serviceName(),
          module,
          action);
      return false;
    }

    String normalizedModule = module.toUpperCase();
    String normalizedAction = action.toUpperCase();

    if (!knownModules().contains(normalizedModule)) {
      log.warn("[{}] hasPermission denied: unknown module '{}'", serviceName(), module);
      return false;
    }

    AuthenticatedUserContext ctx = extractContext(authentication);
    if (ctx == null) {
      log.warn(
          "[{}] AuthenticatedUserContext not set; falling back to role-only check (CR-12-08: "
              + "department enforcement skipped)",
          serviceName());
      return fallbackRoleOnlyCheck(authentication, normalizedAction);
    }

    boolean granted = evaluate(ctx, normalizedModule, normalizedAction);
    // CR-12-11: userId logging kept at DEBUG level only (not INFO/WARN) for GDPR
    log.debug(
        "[{}] hasPermission: role={}, depts={}, module={}, action={} → {}",
        serviceName(),
        ctx.roleCode(),
        ctx.departmentCodes(),
        normalizedModule,
        normalizedAction,
        granted);
    return granted;
  }

  /**
   * Manager-level permission gate (CR-12-06). ADMIN/PLATFORM_ADMIN always pass; MANAGER must be in
   * module-authorized departments; all others denied.
   */
  public boolean hasManagerPermission(Authentication authentication, String module) {
    if (authentication == null || !authentication.isAuthenticated()) {
      log.debug("[{}] hasManagerPermission denied: no valid authentication", serviceName());
      return false;
    }
    if (module == null) {
      log.warn("[{}] hasManagerPermission denied: module is null", serviceName());
      return false;
    }

    String normalizedModule = module.toUpperCase();
    if (!knownModules().contains(normalizedModule)) {
      log.warn("[{}] hasManagerPermission denied: unknown module '{}'", serviceName(), module);
      return false;
    }

    AuthenticatedUserContext ctx = extractContext(authentication);
    if (ctx == null) {
      log.warn("[{}] AuthenticatedUserContext not set; hasManagerPermission denied", serviceName());
      return false;
    }

    String role = ctx.roleCode();
    if (role != null && SUPER_ROLES.contains(role)) return true;
    if (!"MANAGER".equals(role)) return false;

    Set<String> authorizedDepts =
        writeDepartmentsByModule().getOrDefault(normalizedModule, Set.of());
    return ctx.isInAnyDepartment(authorizedDepts);
  }

  // ── Internal evaluation ────────────────────────────────────────────────────

  private boolean evaluate(AuthenticatedUserContext ctx, String module, String action) {
    String role = ctx.roleCode();
    if (role != null && SUPER_ROLES.contains(role)) return true;

    return switch (action) {
      case ACTION_READ -> evaluateRead(ctx, module);
      case ACTION_WRITE -> evaluateWrite(ctx, module);
      default -> {
        log.warn("[{}] Unknown action '{}' for module '{}'", serviceName(), action, module);
        yield false;
      }
    };
  }

  // ── Shared helpers ─────────────────────────────────────────────────────────

  protected AuthenticatedUserContext extractContext(Authentication authentication) {
    if (authentication.getDetails() instanceof AuthenticatedUserContext ctx) return ctx;
    return null;
  }

  /**
   * Safety-net fallback when {@link AuthenticatedUserContext} is unavailable. CR-12-08: This path
   * skips department enforcement and should only occur in test/legacy contexts. All production JWT
   * requests must populate the context.
   */
  protected boolean fallbackRoleOnlyCheck(Authentication authentication, String action) {
    if (ACTION_READ.equals(action)) {
      return authentication.isAuthenticated();
    }
    return authentication.getAuthorities().stream()
        .anyMatch(a -> MANAGEMENT_ROLES.contains(a.getAuthority().replace("ROLE_", "")));
  }

  /** Service bean name for log correlation. Override in subclasses if needed. */
  protected String serviceName() {
    return getClass().getSimpleName();
  }
}
