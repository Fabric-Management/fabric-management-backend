package com.fabricmanagement.flowboard.routing.app;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fabricmanagement.common.infrastructure.security.dto.PermissionResult;
import com.fabricmanagement.flowboard.collection.app.port.CollectionFollowUpPort;
import com.fabricmanagement.flowboard.routing.domain.*;
import com.fabricmanagement.flowboard.routing.domain.port.out.*;
import com.fabricmanagement.platform.user.domain.DataScope;
import java.util.*;
import org.junit.jupiter.api.Test;

class CollectionRoutingEligibilityTest {
  @Test
  void financePoolHasItsOwnScopeAndOrderCoverKeepsSalesRequirements() {
    var tenant = UUID.randomUUID();
    var actor = UUID.randomUUID();
    var obligation = UUID.randomUUID();
    var source = mock(CollectionFollowUpPort.class);
    var sales = mock(SalesOrderWriteScopePort.class);
    var service = new RoutingEligibilityService(mock(RoutingUserQueryPort.class), sales, source);
    var user =
        new RoutingUserQueryPort.UserPermissions(
            tenant,
            actor,
            "Harriet Ward",
            true,
            new PermissionResult(
                Map.of(
                    "flowboard",
                    Map.of("write", DataScope.OWN),
                    "finance",
                    Map.of("read", DataScope.OWN)),
                false));
    when(source.eligible(actor, obligation)).thenReturn(true);
    assertThat(
            service.eligibility(
                tenant, obligation, user, true, RoutingPoolKey.COLLECTION_FOLLOW_UP))
        .isEmpty();
    assertThat(service.candidacy(tenant, user)).contains(RoutingReason.MISSING_SALES_WRITE);
    verifyNoInteractions(sales);
    when(source.eligible(actor, obligation)).thenReturn(false);
    assertThat(
            service.eligibility(
                tenant, obligation, user, true, RoutingPoolKey.COLLECTION_FOLLOW_UP))
        .contains(RoutingReason.OUTSIDE_FINANCE_READ_SCOPE);
    assertThat(
            service.eligibility(
                tenant, obligation, user, false, RoutingPoolKey.COLLECTION_FOLLOW_UP))
        .contains(RoutingReason.NOT_A_MEMBER);
  }
}
