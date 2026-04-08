package com.fabricmanagement.logistics.security;

import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.common.infrastructure.security.BaseAccessService;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Logistics module authorization service.
 *
 * <h2>Permission Matrix</h2>
 *
 * <pre>
 * ADMIN / PLATFORM_ADMIN                        → SHIPMENT R+W
 * MANAGER + Shipping&amp;Transport                  → SHIPMENT R+W
 * MANAGER + Warehouse                           → SHIPMENT R+W
 * MANAGER + Sales&amp;Marketing / Procurement       → SHIPMENT R only
 * SUPERVISOR + Shipping&amp;Transport / Warehouse   → SHIPMENT R+W
 * WORKER + Warehouse                            → SHIPMENT R only
 * </pre>
 *
 * <p><b>CR-12-13:</b> Uses OPERATIONAL_ROLES consistently (ADMIN+MANAGER+SUPERVISOR get read
 * access). WORKER role → department-gated read.
 */
@Service("logisticsAccessService")
public class LogisticsAccessService extends BaseAccessService {

  public static final String MODULE_SHIPMENT = "SHIPMENT";

  private static final Set<String> SHIPMENT_WRITE_DEPARTMENTS =
      Set.of("WAREHOUSE_LOGISTICS", "WAREHOUSE_LOGISTICS");

  private static final Set<String> SHIPMENT_READ_DEPARTMENTS =
      Set.of(
          "WAREHOUSE_LOGISTICS",
          "WAREHOUSE_LOGISTICS",
          "SALES_MARKETING",
          "PROCUREMENT",
          "MANAGEMENT",
          "PRODUCTION");

  private static final Set<String> KNOWN = Set.of(MODULE_SHIPMENT);

  private static final Map<String, Set<String>> WRITE_DEPTS =
      Map.of(MODULE_SHIPMENT, SHIPMENT_WRITE_DEPARTMENTS);

  @Override
  protected Set<String> knownModules() {
    return KNOWN;
  }

  @Override
  protected Map<String, Set<String>> writeDepartmentsByModule() {
    return WRITE_DEPTS;
  }

  /**
   * READ: MANAGER/SUPERVISOR → department-gated. WORKER → department-gated (only WAREHOUSE can
   * read). CR-12-13: Consistent with OPERATIONAL_ROLES pattern.
   */
  @Override
  protected boolean evaluateRead(AuthenticatedUserContext ctx, String module) {
    String role = ctx.roleCode();
    if ("MANAGER".equals(role) || "SUPERVISOR".equals(role) || "WORKER".equals(role)) {
      return ctx.isInAnyDepartment(SHIPMENT_READ_DEPARTMENTS);
    }
    return false;
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
