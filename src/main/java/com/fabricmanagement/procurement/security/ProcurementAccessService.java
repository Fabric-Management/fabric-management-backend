package com.fabricmanagement.procurement.security;

import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * Procurement module authorization service — evaluates <em>role + department</em> together.
 *
 * <p>Designed to be invoked from {@code @PreAuthorize} SpEL expressions:
 *
 * <pre>
 * {@code @PreAuthorize("@procurementAccessService.hasPermission(authentication, 'PURCHASE_ORDER', 'WRITE')")}
 * </pre>
 */
@Service("procurementAccessService")
@Slf4j
public class ProcurementAccessService {

  public static final String MODULE_PURCHASE_ORDER = "PURCHASE_ORDER";
  public static final String MODULE_SUBCONTRACT_ORDER = "SUBCONTRACT_ORDER";

  public static final String ACTION_READ = "READ";
  public static final String ACTION_WRITE = "WRITE";

  private static final Set<String> SUPER_ROLES = Set.of("ADMIN", "PLATFORM_ADMIN");
  private static final Set<String> MANAGEMENT_ROLES = Set.of("ADMIN", "PLATFORM_ADMIN", "MANAGER");

  /** Departments that can write/manage purchase orders. */
  private static final Set<String> PURCHASE_ORDER_WRITE_DEPARTMENTS =
      Set.of("PROCUREMENTSUPPLY", "ADMINISTRATIONOFFICE");

  /** Read visibility. */
  private static final Set<String> ALL_PROCUREMENT_READ_DEPARTMENTS =
      Set.of(
          "PROCUREMENTSUPPLY",
          "ADMINISTRATIONOFFICE",
          "WAREHOUSE",
          "PRODUCTIONPLANNING",
          "RDPRODUCTDEVELOPMENT",
          "SALESCOMMERCIAL");

  private static final Map<String, Set<String>> WRITE_DEPARTMENTS_BY_MODULE =
      Map.of(
          MODULE_PURCHASE_ORDER, PURCHASE_ORDER_WRITE_DEPARTMENTS,
          MODULE_SUBCONTRACT_ORDER, PURCHASE_ORDER_WRITE_DEPARTMENTS);

  private static final Set<String> KNOWN_MODULES =
      Set.of(MODULE_PURCHASE_ORDER, MODULE_SUBCONTRACT_ORDER);

  public boolean hasPermission(Authentication authentication, String module, String action) {
    if (authentication == null || !authentication.isAuthenticated()) {
      return false;
    }
    if (module == null || action == null) {
      return false;
    }
    String normalizedModule = module.toUpperCase();
    String normalizedAction = action.toUpperCase();

    if (!KNOWN_MODULES.contains(normalizedModule)) {
      log.warn("hasPermission denied: unknown module '{}'", module);
      return false;
    }

    AuthenticatedUserContext ctx = extractContext(authentication);
    if (ctx == null) {
      return fallbackRoleOnlyCheck(authentication, normalizedAction);
    }
    return evaluate(ctx, normalizedModule, normalizedAction);
  }

  private boolean evaluate(AuthenticatedUserContext ctx, String module, String action) {
    String role = ctx.roleCode();
    if (role != null && SUPER_ROLES.contains(role)) {
      return true;
    }
    return switch (action) {
      case ACTION_READ -> ctx.isInAnyDepartment(ALL_PROCUREMENT_READ_DEPARTMENTS);
      case ACTION_WRITE -> {
        if ("MANAGER".equals(role) || "SUPERVISOR".equals(role)) {
          yield ctx.isInAnyDepartment(WRITE_DEPARTMENTS_BY_MODULE.getOrDefault(module, Set.of()));
        }
        yield false;
      }
      default -> false;
    };
  }

  private AuthenticatedUserContext extractContext(Authentication authentication) {
    if (authentication.getDetails() instanceof AuthenticatedUserContext ctx) {
      return ctx;
    }
    return null;
  }

  private boolean fallbackRoleOnlyCheck(Authentication authentication, String action) {
    if (ACTION_READ.equals(action)) {
      return authentication.isAuthenticated();
    }
    return authentication.getAuthorities().stream()
        .anyMatch(a -> MANAGEMENT_ROLES.contains(a.getAuthority().replace("ROLE_", "")));
  }
}
