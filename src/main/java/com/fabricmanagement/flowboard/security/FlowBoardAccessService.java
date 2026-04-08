package com.fabricmanagement.flowboard.security;

import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.common.infrastructure.security.BaseAccessService;
import java.util.Map;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * FlowBoard module access service.
 *
 * <p>Read: All authenticated users can read board and task data for their tenant. Write: MANAGER
 * and ADMIN roles can create/update boards; regular users can update task status.
 */
@Service("flowBoardAccessService")
public class FlowBoardAccessService extends BaseAccessService {

  private static final Set<String> MODULES =
      Set.of("BOARD", "TASK", "DASHBOARD", "PERFORMANCE", "RECURRING_TEMPLATE");

  private static final Map<String, Set<String>> WRITE_DEPARTMENTS =
      Map.of(
          "BOARD", Set.of("MANAGEMENT", "PRODUCTION"),
          "TASK",
              Set.of(
                  "MANAGEMENT",
                  "PRODUCTION",
                  "PRODUCTION",
                  "WAREHOUSE_LOGISTICS",
                  "QUALITY_CONTROL"),
          "DASHBOARD", Set.of("MANAGEMENT"),
          "PERFORMANCE", Set.of("MANAGEMENT"),
          "RECURRING_TEMPLATE", Set.of("MANAGEMENT", "PRODUCTION"));

  @Override
  protected Set<String> knownModules() {
    return MODULES;
  }

  @Override
  protected Map<String, Set<String>> writeDepartmentsByModule() {
    return WRITE_DEPARTMENTS;
  }

  @Override
  protected boolean evaluateRead(AuthenticatedUserContext ctx, String module) {
    return true;
  }

  @Override
  protected boolean evaluateWrite(AuthenticatedUserContext ctx, String module) {
    if (MANAGEMENT_ROLES.contains(ctx.roleCode())) {
      return true;
    }
    if ("TASK".equals(module)) {
      return true;
    }
    Set<String> allowedDepts = writeDepartmentsByModule().get(module);
    if (allowedDepts == null) return false;
    return allowedDepts.stream().anyMatch(d -> ctx.isInDepartment(d));
  }

  public boolean canRead(Authentication auth, String module) {
    return hasPermission(auth, module, ACTION_READ);
  }

  public boolean canWrite(Authentication auth, String module) {
    return hasPermission(auth, module, ACTION_WRITE);
  }
}
