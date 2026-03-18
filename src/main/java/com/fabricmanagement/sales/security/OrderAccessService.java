package com.fabricmanagement.sales.security;

import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.common.infrastructure.security.BaseAccessService;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Order module authorization service.
 *
 * <h2>Permission Matrix</h2>
 *
 * <pre>
 * ADMIN / PLATFORM_ADMIN          → SALES_ORDER R+W
 * MANAGER + Sales&amp;Marketing       → SALES_ORDER R+W
 * MANAGER + Production Planning   → SALES_ORDER R+W
 * MANAGER + Administration Office → SALES_ORDER R+W
 * MANAGER + Management&amp;Planning   → SALES_ORDER R+W
 * MANAGER + Procurement&amp;Supply    → SALES_ORDER R only
 * SUPERVISOR + authorized depts   → SALES_ORDER R+W
 * WORKER / VIEWER                 → SALES_ORDER R only (if in read departments)
 * </pre>
 *
 * <p><b>CR-12-03:</b> WORKER/VIEWER role: OPERATIONAL_ROLES check covers ADMIN/MANAGER/SUPERVISOR.
 * Workers reach the department fallback and get READ only if they belong to {@code
 * ALL_ORDER_READ_DEPARTMENTS} (e.g., WAREHOUSE, FIBERRAWMATERIAL). Workers not in those departments
 * are denied.
 */
@Service("orderAccessService")
public class OrderAccessService extends BaseAccessService {

  public static final String MODULE_SALES_ORDER = "SALES_ORDER";

  private static final Set<String> SALES_ORDER_WRITE_DEPARTMENTS =
      Set.of("SALESMARKETING", "PRODUCTIONPLANNING", "ADMINISTRATIONOFFICE", "MANAGEMENTPLANNING");

  private static final Set<String> ALL_ORDER_READ_DEPARTMENTS =
      Set.of(
          "SALESMARKETING",
          "PRODUCTIONPLANNING",
          "ADMINISTRATIONOFFICE",
          "MANAGEMENTPLANNING",
          "PROCUREMENTSUPPLY",
          "FIBERRAWMATERIAL",
          "WAREHOUSE");

  private static final Set<String> KNOWN = Set.of(MODULE_SALES_ORDER);

  private static final Map<String, Set<String>> WRITE_DEPTS =
      Map.of(MODULE_SALES_ORDER, SALES_ORDER_WRITE_DEPARTMENTS);

  @Override
  protected Set<String> knownModules() {
    return KNOWN;
  }

  @Override
  protected Map<String, Set<String>> writeDepartmentsByModule() {
    return WRITE_DEPTS;
  }

  /**
   * READ: MANAGER/SUPERVISOR → always true (need full visibility). WORKER/VIEWER → department-gated
   * (CR-12-03).
   */
  @Override
  protected boolean evaluateRead(AuthenticatedUserContext ctx, String module) {
    String role = ctx.roleCode();
    if (role != null && OPERATIONAL_ROLES.contains(role)) return true;
    // WORKER/VIEWER reach here → check department membership
    return ctx.isInAnyDepartment(ALL_ORDER_READ_DEPARTMENTS);
  }

  @Override
  protected boolean evaluateWrite(AuthenticatedUserContext ctx, String module) {
    Set<String> authorizedDepts = writeDepartmentsByModule().getOrDefault(module, Set.of());
    String role = ctx.roleCode();
    if ("MANAGER".equals(role) || "SUPERVISOR".equals(role)) {
      return ctx.isInAnyDepartment(authorizedDepts);
    }
    return false;
  }
}
