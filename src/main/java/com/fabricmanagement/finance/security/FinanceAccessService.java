package com.fabricmanagement.finance.security;

import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.common.infrastructure.security.BaseAccessService;
import java.util.Map;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * Finance module authorization service.
 *
 * <h2>Permission Matrix</h2>
 *
 * <pre>
 * ADMIN / PLATFORM_ADMIN                      → INVOICE R+W
 * MANAGER + Finance&amp;Accounting                → INVOICE R+W
 * MANAGER + Administration Office             → INVOICE R+W
 * MANAGER + Sales&amp;Marketing / Procurement     → INVOICE R only
 * SUPERVISOR + Finance&amp;Accounting             → INVOICE R+W
 * WORKER / VIEWER                             → denied
 * </pre>
 *
 * <p><b>CR-12-02 fix:</b> Finance fallback intentionally restricts READ to MANAGEMENT_ROLES (not
 * isAuthenticated) because financial data is sensitive. This is documented, not a bug.
 */
@Service("financeAccessService")
public class FinanceAccessService extends BaseAccessService {

  public static final String MODULE_INVOICE = "INVOICE";

  private static final Set<String> INVOICE_WRITE_DEPARTMENTS =
      Set.of("FINANCE_ACCOUNTING", "MANAGEMENT");

  private static final Set<String> INVOICE_READ_DEPARTMENTS =
      Set.of("FINANCE_ACCOUNTING", "MANAGEMENT", "SALES_MARKETING", "PROCUREMENT", "MANAGEMENT");

  private static final Set<String> KNOWN = Set.of(MODULE_INVOICE);

  private static final Map<String, Set<String>> WRITE_DEPTS =
      Map.of(MODULE_INVOICE, INVOICE_WRITE_DEPARTMENTS);

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
    // Finance: Only MANAGER/SUPERVISOR with authorized departments can read.
    // WORKER/VIEWER have no access (financial data is sensitive).
    if ("MANAGER".equals(role) || "SUPERVISOR".equals(role)) {
      return ctx.isInAnyDepartment(INVOICE_READ_DEPARTMENTS);
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

  /**
   * CR-12-02 fix: Finance fallback intentionally restricts both READ and WRITE to MANAGEMENT_ROLES.
   * Unlike other modules, we do NOT return {@code isAuthenticated()} for READ because financial
   * data (invoices, amounts) should never be visible to arbitrary authenticated users.
   */
  @Override
  protected boolean fallbackRoleOnlyCheck(Authentication authentication, String action) {
    return authentication.getAuthorities().stream()
        .anyMatch(a -> MANAGEMENT_ROLES.contains(a.getAuthority().replace("ROLE_", "")));
  }
}
