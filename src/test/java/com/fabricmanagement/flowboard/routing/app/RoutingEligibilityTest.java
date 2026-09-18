package com.fabricmanagement.flowboard.routing.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.fabricmanagement.common.infrastructure.security.dto.PermissionResult;
import com.fabricmanagement.flowboard.routing.domain.RoutingReason;
import com.fabricmanagement.flowboard.routing.domain.port.out.*;
import com.fabricmanagement.platform.user.domain.DataScope;
import java.util.*;
import org.junit.jupiter.api.Test;

class RoutingEligibilityTest {
  private final UUID tenant = UUID.randomUUID(),
      user = UUID.randomUUID(),
      order = UUID.randomUUID();
  private final SalesOrderWriteScopePort scope = mock(SalesOrderWriteScopePort.class);
  private final RoutingEligibilityService eligibility =
      new RoutingEligibilityService(mock(RoutingUserQueryPort.class), scope);

  private RoutingUserQueryPort.UserPermissions identity(
      UUID tenantId, boolean active, boolean flowboard, boolean sales, boolean admin) {
    Map<String, Map<String, DataScope>> grants = new HashMap<>();
    if (flowboard) grants.put("flowboard", Map.of("write", DataScope.GLOBAL));
    if (sales) grants.put("sales", Map.of("write", DataScope.GLOBAL));
    return new RoutingUserQueryPort.UserPermissions(
        tenantId, user, "User", active, new PermissionResult(grants, admin));
  }

  @Test
  void eachCandidateConditionAndItsReason() {
    assertThat(eligibility.candidacy(tenant, identity(tenant, false, true, true, false)))
        .containsExactly(RoutingReason.INACTIVE);
    assertThat(eligibility.candidacy(tenant, identity(UUID.randomUUID(), true, true, true, false)))
        .containsExactly(RoutingReason.INACTIVE);
    assertThat(eligibility.candidacy(tenant, identity(tenant, true, false, true, false)))
        .containsExactly(RoutingReason.MISSING_FLOWBOARD_WRITE);
    assertThat(eligibility.candidacy(tenant, identity(tenant, true, true, false, false)))
        .containsExactly(RoutingReason.MISSING_SALES_WRITE);
    assertThat(eligibility.candidacy(tenant, identity(tenant, true, true, true, false))).isEmpty();
  }

  @Test
  void membershipAndScopeFailIndependently() {
    var candidate = identity(tenant, true, true, true, false);
    assertThat(eligibility.eligibility(tenant, order, candidate, false))
        .containsExactly(RoutingReason.NOT_A_MEMBER);
    verifyNoInteractions(scope);
    when(scope.isAllowed(tenant, user, order)).thenReturn(false);
    assertThat(eligibility.eligibility(tenant, order, candidate, true))
        .containsExactly(RoutingReason.OUTSIDE_SALES_WRITE_SCOPE);
    when(scope.isAllowed(tenant, user, order)).thenReturn(true);
    assertThat(eligibility.eligibility(tenant, order, candidate, true)).isEmpty();
  }

  @Test
  void bypassOnlyAnswersPermissionQuestion() {
    assertThat(eligibility.candidacy(tenant, identity(tenant, true, false, false, true))).isEmpty();
    assertThat(eligibility.candidacy(tenant, identity(tenant, false, false, false, true)))
        .containsExactly(RoutingReason.INACTIVE);
    assertThat(eligibility.candidacy(tenant, identity(UUID.randomUUID(), true, false, false, true)))
        .containsExactly(RoutingReason.INACTIVE);
    assertThat(
            eligibility.eligibility(
                tenant, order, identity(tenant, true, false, false, true), false))
        .containsExactly(RoutingReason.NOT_A_MEMBER);
    assertThat(
            eligibility.eligibility(
                tenant, order, identity(tenant, true, false, false, true), true))
        .containsExactly(RoutingReason.OUTSIDE_SALES_WRITE_SCOPE);
  }
}
