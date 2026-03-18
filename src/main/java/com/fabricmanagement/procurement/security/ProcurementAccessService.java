package com.fabricmanagement.procurement.security;

import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.common.infrastructure.security.BaseAccessService;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Procurement module authorization service.
 *
 * <p>Supports modules: {@code PURCHASE_ORDER}, {@code SUBCONTRACT_ORDER}, {@code SUPPLIER_RFQ},
 * {@code SUPPLIER_QUOTE}.
 *
 * <h2>Permission Matrix</h2>
 *
 * <pre>
 * ADMIN / PLATFORM_ADMIN                      → all modules R+W
 * MANAGER + Procurement&amp;Supply / Admin Office → all modules R+W
 * SUPERVISOR + Procurement&amp;Supply             → all modules R+W
 * Others in read departments                  → R only
 * </pre>
 */
@Service("procurementAccessService")
public class ProcurementAccessService extends BaseAccessService {

  public static final String MODULE_PURCHASE_ORDER = "PURCHASE_ORDER";
  public static final String MODULE_SUBCONTRACT_ORDER = "SUBCONTRACT_ORDER";
  public static final String MODULE_SUPPLIER_RFQ = "SUPPLIER_RFQ";
  public static final String MODULE_SUPPLIER_QUOTE = "SUPPLIER_QUOTE";

  private static final Set<String> PROCUREMENT_WRITE_DEPARTMENTS =
      Set.of("PROCUREMENTSUPPLY", "ADMINISTRATIONOFFICE");

  private static final Set<String> ALL_PROCUREMENT_READ_DEPARTMENTS =
      Set.of(
          "PROCUREMENTSUPPLY",
          "ADMINISTRATIONOFFICE",
          "WAREHOUSE",
          "PRODUCTIONPLANNING",
          "RDPRODUCTDEVELOPMENT",
          "SALESCOMMERCIAL");

  private static final Set<String> KNOWN =
      Set.of(
          MODULE_PURCHASE_ORDER,
          MODULE_SUBCONTRACT_ORDER,
          MODULE_SUPPLIER_RFQ,
          MODULE_SUPPLIER_QUOTE);

  private static final Map<String, Set<String>> WRITE_DEPTS =
      Map.of(
          MODULE_PURCHASE_ORDER, PROCUREMENT_WRITE_DEPARTMENTS,
          MODULE_SUBCONTRACT_ORDER, PROCUREMENT_WRITE_DEPARTMENTS,
          MODULE_SUPPLIER_RFQ, PROCUREMENT_WRITE_DEPARTMENTS,
          MODULE_SUPPLIER_QUOTE, PROCUREMENT_WRITE_DEPARTMENTS);

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
    if (role != null && OPERATIONAL_ROLES.contains(role)) {
      return ctx.isInAnyDepartment(ALL_PROCUREMENT_READ_DEPARTMENTS);
    }
    return ctx.isInAnyDepartment(ALL_PROCUREMENT_READ_DEPARTMENTS);
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
