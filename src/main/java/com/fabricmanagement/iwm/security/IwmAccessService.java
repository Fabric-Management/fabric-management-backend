package com.fabricmanagement.iwm.security;

import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.common.infrastructure.security.BaseAccessService;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * IWM (Intelligent Warehouse Management) module authorization service (CR-12-10).
 *
 * <p>Replaces the simple {@code hasAuthority('INVENTORY_WRITE')} checks with the standard Faz 12
 * role+department pattern.
 *
 * <h2>Permission Matrix</h2>
 *
 * <pre>
 * ADMIN / PLATFORM_ADMIN                → all IWM modules R+W
 * MANAGER + Warehouse                   → all IWM modules R+W
 * MANAGER + Production Planning         → STOCK_RESERVATION, STOCK_TRANSFER R+W; rest R only
 * MANAGER + Quality Control             → STOCK_COUNT R+W; rest R only
 * SUPERVISOR + Warehouse                → all IWM modules R+W
 * WORKER + Warehouse                    → R only
 * </pre>
 */
@Service("iwmAccessService")
public class IwmAccessService extends BaseAccessService {

  public static final String MODULE_LOCATION = "LOCATION";
  public static final String MODULE_STOCK_RESERVATION = "STOCK_RESERVATION";
  public static final String MODULE_STOCK_RULE = "STOCK_RULE";
  public static final String MODULE_STOCK_COUNT = "STOCK_COUNT";
  public static final String MODULE_STOCK_TRANSFER = "STOCK_TRANSFER";
  public static final String MODULE_RMA = "RMA";
  public static final String MODULE_STOCK_ADJUSTMENT = "STOCK_ADJUSTMENT";

  private static final Set<String> WAREHOUSE_WRITE_DEPARTMENTS =
      Set.of("WAREHOUSE_LOGISTICS", "PRODUCTION");

  private static final Set<String> STOCK_COUNT_WRITE_DEPARTMENTS =
      Set.of("WAREHOUSE_LOGISTICS", "QUALITY_CONTROL");

  private static final Set<String> IWM_READ_DEPARTMENTS =
      Set.of(
          "WAREHOUSE_LOGISTICS",
          "PRODUCTION",
          "QUALITY_CONTROL",
          "PROCUREMENT",
          "PROCUREMENT",
          "SALES_MARKETING");

  private static final Set<String> KNOWN =
      Set.of(
          MODULE_LOCATION,
          MODULE_STOCK_RESERVATION,
          MODULE_STOCK_RULE,
          MODULE_STOCK_COUNT,
          MODULE_STOCK_TRANSFER,
          MODULE_RMA,
          MODULE_STOCK_ADJUSTMENT);

  private static final Map<String, Set<String>> WRITE_DEPTS =
      Map.of(
          MODULE_LOCATION, WAREHOUSE_WRITE_DEPARTMENTS,
          MODULE_STOCK_RESERVATION, WAREHOUSE_WRITE_DEPARTMENTS,
          MODULE_STOCK_RULE, WAREHOUSE_WRITE_DEPARTMENTS,
          MODULE_STOCK_COUNT, STOCK_COUNT_WRITE_DEPARTMENTS,
          MODULE_STOCK_TRANSFER, WAREHOUSE_WRITE_DEPARTMENTS,
          MODULE_RMA, WAREHOUSE_WRITE_DEPARTMENTS,
          MODULE_STOCK_ADJUSTMENT, WAREHOUSE_WRITE_DEPARTMENTS);

  @Override
  protected Set<String> knownModules() {
    return KNOWN;
  }

  @Override
  protected Map<String, Set<String>> writeDepartmentsByModule() {
    return WRITE_DEPTS;
  }

  @Override
  protected boolean evaluateRead(AuthenticatedUserContext ctx, String module) {
    String role = ctx.roleCode();
    if (role != null && OPERATIONAL_ROLES.contains(role)) return true;
    return ctx.isInAnyDepartment(IWM_READ_DEPARTMENTS);
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
