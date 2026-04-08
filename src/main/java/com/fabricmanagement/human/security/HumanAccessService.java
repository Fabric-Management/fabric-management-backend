package com.fabricmanagement.human.security;

import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.common.infrastructure.security.BaseAccessService;
import java.util.Map;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * Human (HR) module authorization service (CR-12-15).
 *
 * <h2>Permission Matrix</h2>
 *
 * <pre>
 * ADMIN / PLATFORM_ADMIN     → all HR modules R+W
 * MANAGER + HR Department    → EMPLOYEE, PAYROLL, LEAVE R+W
 * MANAGER + any department   → LEAVE R+W (own department subordinates)
 * authenticated user         → SELF_SERVICE R only (own profile, own payroll)
 * </pre>
 *
 * <p><b>Note:</b> Self-service endpoints ({@code /api/human/employees/me}, {@code
 * /api/human/payroll/me}) should use {@code isAuthenticated()} directly on the controller rather
 * than this service, since every authenticated user can see their own data.
 */
@Service("humanAccessService")
public class HumanAccessService extends BaseAccessService {

  public static final String MODULE_EMPLOYEE = "EMPLOYEE";
  public static final String MODULE_PAYROLL = "PAYROLL";
  public static final String MODULE_LEAVE = "LEAVE";

  private static final Set<String> HR_WRITE_DEPARTMENTS = Set.of("HUMAN_RESOURCES", "MANAGEMENT");

  private static final Set<String> KNOWN = Set.of(MODULE_EMPLOYEE, MODULE_PAYROLL, MODULE_LEAVE);

  private static final Map<String, Set<String>> WRITE_DEPTS =
      Map.of(
          MODULE_EMPLOYEE, HR_WRITE_DEPARTMENTS,
          MODULE_PAYROLL, HR_WRITE_DEPARTMENTS,
          MODULE_LEAVE, HR_WRITE_DEPARTMENTS);

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
    // MANAGER/SUPERVISOR can read HR data (e.g. leave requests of subordinates)
    if (role != null && OPERATIONAL_ROLES.contains(role)) {
      return true;
    }
    // WORKER → HR read only if in HR department
    return ctx.isInAnyDepartment(HR_WRITE_DEPARTMENTS);
  }

  @Override
  protected boolean evaluateWrite(AuthenticatedUserContext ctx, String module) {
    Set<String> authorizedDepts = writeDepartmentsByModule().getOrDefault(module, Set.of());
    String role = ctx.roleCode();
    if ("MANAGER".equals(role)) {
      // LEAVE: any manager can approve their department's leave requests
      if (MODULE_LEAVE.equals(module)) return true;
      return ctx.isInAnyDepartment(authorizedDepts);
    }
    if ("SUPERVISOR".equals(role)) {
      return ctx.isInAnyDepartment(authorizedDepts);
    }
    return false;
  }

  /**
   * HR fallback: READ returns authenticated (self-service is basic), WRITE requires management
   * role.
   */
  @Override
  protected boolean fallbackRoleOnlyCheck(Authentication authentication, String action) {
    if (ACTION_READ.equals(action)) return authentication.isAuthenticated();
    return authentication.getAuthorities().stream()
        .anyMatch(a -> MANAGEMENT_ROLES.contains(a.getAuthority().replace("ROLE_", "")));
  }
}
