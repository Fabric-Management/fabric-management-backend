package com.fabricmanagement.production.security;

import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.common.infrastructure.security.BaseAccessService;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Production module authorization service — evaluates <em>role + department</em> together.
 *
 * <h2>Permission Matrix</h2>
 *
 * <pre>
 * ┌────────────────────────────┬───────────────────┬──────────────┬──────────────┬──────────────────────┐
 * │ Role / Department          │ FIBER / MATERIAL  │ BATCH        │ QUALITY_TEST │ WAREHOUSE_LOCATION   │
 * ├────────────────────────────┼───────────────────┼──────────────┼──────────────┼──────────────────────┤
 * │ ADMIN (any dept)           │ READ + WRITE      │ READ + WRITE │ READ + WRITE │ READ + WRITE         │
 * │ MANAGER + R&amp;D/Prod.Dev.    │ READ + WRITE      │ READ + WRITE │ READ + WRITE │ READ only            │
 * │ MANAGER + Prod.Planning    │ READ + WRITE      │ READ + WRITE │ READ only    │ READ + WRITE         │
 * │ MANAGER + Fiber&amp;RawMat.    │ READ + WRITE      │ READ + WRITE │ READ + WRITE │ READ only            │
 * │ MANAGER + QualityControl   │ READ only         │ READ + WRITE │ READ + WRITE │ READ only            │
 * │ MANAGER + Warehouse        │ READ only         │ READ + WRITE │ READ only    │ READ + WRITE         │
 * │ SUPERVISOR + QC/R&amp;D/Fiber  │ READ only         │ READ + WRITE │ READ + WRITE │ READ only            │
 * │ SUPERVISOR + Warehouse     │ READ only         │ READ + WRITE │ READ only    │ READ + WRITE         │
 * │ WORKER / VIEWER + any dept │ READ only         │ READ only    │ READ only    │ READ only            │
 * └────────────────────────────┴───────────────────┴──────────────┴──────────────┴──────────────────────┘
 * </pre>
 *
 * <h2>Department Codes</h2>
 *
 * <ul>
 *   <li>{@code RDPRODUCTDEVELOPMENT} — R&amp;D / Product Development
 *   <li>{@code PRODUCTIONPLANNING} — Production Planning
 *   <li>{@code FIBERRAWMATERIAL} — Fiber &amp; Raw Material
 *   <li>{@code YARNPRODUCTION} — Yarn Production
 *   <li>{@code WEAVINGKNITTING} — Weaving &amp; Knitting
 *   <li>{@code DYEINGFINISHING} — Dyeing &amp; Finishing
 *   <li>{@code QUALITYCONTROL} — Quality Control
 *   <li>{@code WAREHOUSE} — Warehouse
 * </ul>
 */
@Service("productionAccessService")
public class ProductionAccessService extends BaseAccessService {

  public static final String MODULE_FIBER = "FIBER";
  public static final String MODULE_MATERIAL = "MATERIAL";
  public static final String MODULE_BATCH = "BATCH";
  public static final String MODULE_QUALITY_TEST = "QUALITY_TEST";
  public static final String MODULE_WAREHOUSE_LOCATION = "WAREHOUSE_LOCATION";
  public static final String MODULE_GOODS_RECEIPT = "GOODS_RECEIPT";
  public static final String MODULE_STOCK_UNIT = "STOCK_UNIT";
  public static final String MODULE_QUALITY_GRADE = "QUALITY_GRADE";

  private static final Set<String> FIBER_MASTERDATA_WRITE_DEPARTMENTS =
      Set.of(
          "RDPRODUCTDEVELOPMENT", "PRODUCTIONPLANNING", "FIBERRAWMATERIAL", "ADMINISTRATIONOFFICE");

  private static final Set<String> BATCH_WRITE_DEPARTMENTS =
      Set.of(
          "RDPRODUCTDEVELOPMENT",
          "PRODUCTIONPLANNING",
          "FIBERRAWMATERIAL",
          "YARNPRODUCTION",
          "WEAVINGKNITTING",
          "DYEINGFINISHING",
          "QUALITYCONTROL",
          "WAREHOUSE");

  private static final Set<String> QUALITY_TEST_WRITE_DEPARTMENTS =
      Set.of("QUALITYCONTROL", "RDPRODUCTDEVELOPMENT", "FIBERRAWMATERIAL");

  private static final Set<String> WAREHOUSE_LOCATION_WRITE_DEPARTMENTS =
      Set.of("WAREHOUSE", "PRODUCTIONPLANNING");

  private static final Set<String> GOODS_RECEIPT_WRITE_DEPARTMENTS = Set.of("WAREHOUSE");

  private static final Set<String> STOCK_UNIT_WRITE_DEPARTMENTS =
      Set.of("WAREHOUSE", "PRODUCTIONPLANNING");

  private static final Set<String> QUALITY_GRADE_WRITE_DEPARTMENTS =
      Set.of("QUALITYCONTROL", "RDPRODUCTDEVELOPMENT");

  private static final Set<String> ALL_PRODUCTION_READ_DEPARTMENTS =
      Set.of(
          "RDPRODUCTDEVELOPMENT",
          "PRODUCTIONPLANNING",
          "FIBERRAWMATERIAL",
          "YARNPRODUCTION",
          "WEAVINGKNITTING",
          "DYEINGFINISHING",
          "QUALITYCONTROL",
          "WAREHOUSE",
          "PROCUREMENTSUPPLY");

  private static final Set<String> KNOWN =
      Set.of(
          MODULE_FIBER,
          MODULE_MATERIAL,
          MODULE_BATCH,
          MODULE_QUALITY_TEST,
          MODULE_WAREHOUSE_LOCATION,
          MODULE_GOODS_RECEIPT,
          MODULE_STOCK_UNIT,
          MODULE_QUALITY_GRADE);

  private static final Map<String, Set<String>> WRITE_DEPTS =
      Map.ofEntries(
          Map.entry(MODULE_FIBER, FIBER_MASTERDATA_WRITE_DEPARTMENTS),
          Map.entry(MODULE_MATERIAL, FIBER_MASTERDATA_WRITE_DEPARTMENTS),
          Map.entry(MODULE_BATCH, BATCH_WRITE_DEPARTMENTS),
          Map.entry(MODULE_QUALITY_TEST, QUALITY_TEST_WRITE_DEPARTMENTS),
          Map.entry(MODULE_WAREHOUSE_LOCATION, WAREHOUSE_LOCATION_WRITE_DEPARTMENTS),
          Map.entry(MODULE_GOODS_RECEIPT, GOODS_RECEIPT_WRITE_DEPARTMENTS),
          Map.entry(MODULE_STOCK_UNIT, STOCK_UNIT_WRITE_DEPARTMENTS),
          Map.entry(MODULE_QUALITY_GRADE, QUALITY_GRADE_WRITE_DEPARTMENTS));

  @Override
  protected Set<String> knownModules() {
    return KNOWN;
  }

  @Override
  protected Map<String, Set<String>> writeDepartmentsByModule() {
    return WRITE_DEPTS;
  }

  /**
   * READ: MANAGER/SUPERVISOR → read all known production modules (they need full visibility).
   * WORKER/VIEWER → read only if assigned to a production-related department.
   */
  @Override
  protected boolean evaluateRead(AuthenticatedUserContext ctx, String module) {
    String role = ctx.roleCode();
    if (role != null && OPERATIONAL_ROLES.contains(role)) return true;
    return ctx.isInAnyDepartment(ALL_PRODUCTION_READ_DEPARTMENTS);
  }

  /**
   * WRITE: FIBER/MATERIAL → MANAGER + fiber masterdata department. BATCH → MANAGER or SUPERVISOR +
   * production-adjacent department. QUALITY_TEST → MANAGER or SUPERVISOR + QC/R&amp;D/Fiber dept.
   */
  @Override
  protected boolean evaluateWrite(AuthenticatedUserContext ctx, String module) {
    Set<String> authorizedDepts = writeDepartmentsByModule().getOrDefault(module, Set.of());
    String role = ctx.roleCode();

    if ("MANAGER".equals(role)) {
      return ctx.isInAnyDepartment(authorizedDepts);
    }

    if ("SUPERVISOR".equals(role)
        && (MODULE_BATCH.equals(module)
            || MODULE_QUALITY_TEST.equals(module)
            || MODULE_WAREHOUSE_LOCATION.equals(module)
            || MODULE_GOODS_RECEIPT.equals(module)
            || MODULE_STOCK_UNIT.equals(module))) {
      return ctx.isInAnyDepartment(authorizedDepts);
    }

    return false;
  }
}
