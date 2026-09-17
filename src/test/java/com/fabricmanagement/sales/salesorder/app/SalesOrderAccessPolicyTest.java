package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.PermissionEvaluator;
import com.fabricmanagement.common.infrastructure.security.dto.PermissionResult;
import com.fabricmanagement.platform.user.app.UserQueryService;
import com.fabricmanagement.platform.user.app.UserQueryService.PermissionIdentity;
import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.platform.user.domain.SystemUser;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SalesOrderAccessPolicyTest {

  private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
  private static final UUID OTHER_TENANT_ID =
      UUID.fromString("10000000-0000-0000-0000-000000000002");
  private static final UUID TARGET_USER_ID =
      UUID.fromString("20000000-0000-0000-0000-000000000001");
  private static final UUID DEPARTMENT_MEMBER_ID =
      UUID.fromString("20000000-0000-0000-0000-000000000002");
  private static final UUID OUTSIDER_ID = UUID.fromString("20000000-0000-0000-0000-000000000003");

  @Mock private PermissionEvaluator permissionEvaluator;
  @Mock private UserQueryService userQueryService;

  private SalesOrderAccessPolicy policy;

  @BeforeEach
  void setUp() {
    policy = new SalesOrderAccessPolicy(permissionEvaluator, userQueryService);
    lenient()
        .when(userQueryService.findPermissionIdentity(TENANT_ID, TARGET_USER_ID))
        .thenReturn(Optional.of(new PermissionIdentity("WORKER", List.of("SALES"))));
  }

  @AfterEach
  void clearContext() {
    TenantContext.clear();
  }

  @ParameterizedTest
  @EnumSource(DataScope.class)
  void eachReadScopeAllowsItsMatchingOrder(DataScope scope) {
    UUID creator = matchingCreator(scope);
    stubPermissions(scope, DataScope.OWN);
    stubDepartmentMembersIfNeeded(scope);

    assertThat(policy.canRead(TENANT_ID, TARGET_USER_ID, order(TENANT_ID, creator))).isTrue();
  }

  @ParameterizedTest
  @EnumSource(DataScope.class)
  void eachWriteScopeAllowsItsMatchingOrder(DataScope scope) {
    UUID creator = matchingCreator(scope);
    stubPermissions(DataScope.OWN, scope);
    stubDepartmentMembersIfNeeded(scope);

    assertThat(policy.canWrite(TENANT_ID, TARGET_USER_ID, order(TENANT_ID, creator))).isTrue();
  }

  @Test
  void readAndWriteScopesAreResolvedSeparately() {
    stubPermissions(DataScope.OWN, DataScope.GLOBAL);
    SalesOrder order = order(TENANT_ID, OUTSIDER_ID);

    assertThat(policy.canRead(TENANT_ID, TARGET_USER_ID, order)).isFalse();
    assertThat(policy.canWrite(TENANT_ID, TARGET_USER_ID, order)).isTrue();
  }

  @Test
  void ownAndDepartmentDenyUnknownCreator() {
    stubPermissions(DataScope.OWN, DataScope.DEPARTMENT);
    when(userQueryService.findActiveUserIdsByDepartmentCodes(TENANT_ID, Set.of("SALES")))
        .thenReturn(Set.of(DEPARTMENT_MEMBER_ID));
    SalesOrder order = order(TENANT_ID, null);

    assertThat(policy.canRead(TENANT_ID, TARGET_USER_ID, order)).isFalse();
    assertThat(policy.canWrite(TENANT_ID, TARGET_USER_ID, order)).isFalse();
  }

  @Test
  void missingPermissionPairDeniesReadAndWrite() {
    when(permissionEvaluator.evaluate(TENANT_ID, "WORKER", List.of("SALES"), TARGET_USER_ID))
        .thenReturn(new PermissionResult(Map.of(), false));
    SalesOrder order = order(TENANT_ID, TARGET_USER_ID);

    assertThat(policy.canRead(TENANT_ID, TARGET_USER_ID, order)).isFalse();
    assertThat(policy.canWrite(TENANT_ID, TARGET_USER_ID, order)).isFalse();
  }

  @Test
  void departmentScopeIncludesTargetUser() {
    stubPermissions(DataScope.DEPARTMENT, DataScope.DEPARTMENT);
    when(userQueryService.findActiveUserIdsByDepartmentCodes(TENANT_ID, Set.of("SALES")))
        .thenReturn(Set.of());
    SalesOrder order = order(TENANT_ID, TARGET_USER_ID);

    assertThat(policy.canRead(TENANT_ID, TARGET_USER_ID, order)).isTrue();
    assertThat(policy.canWrite(TENANT_ID, TARGET_USER_ID, order)).isTrue();
  }

  @Test
  void systemCallerContextConfersNothingOnTargetUser() {
    TenantContext.setCurrentTenantId(TENANT_ID);
    TenantContext.setCurrentUserId(SystemUser.ID);
    stubPermissions(DataScope.OWN, DataScope.OWN);
    SalesOrder outsiderOrder = order(TENANT_ID, OUTSIDER_ID);

    assertThat(policy.canRead(TENANT_ID, TARGET_USER_ID, outsiderOrder)).isFalse();
    assertThat(policy.canWrite(TENANT_ID, TARGET_USER_ID, outsiderOrder)).isFalse();
  }

  @Test
  void globalScopeNeverCrossesTenantBoundary() {
    SalesOrder otherTenantOrder = order(OTHER_TENANT_ID, TARGET_USER_ID);

    assertThat(policy.canRead(TENANT_ID, TARGET_USER_ID, otherTenantOrder)).isFalse();
    assertThat(policy.canWrite(TENANT_ID, TARGET_USER_ID, otherTenantOrder)).isFalse();
  }

  private void stubPermissions(DataScope readScope, DataScope writeScope) {
    PermissionResult permissions =
        new PermissionResult(
            Map.of("sales", Map.of("read", readScope, "write", writeScope)), false);
    when(permissionEvaluator.evaluate(TENANT_ID, "WORKER", List.of("SALES"), TARGET_USER_ID))
        .thenReturn(permissions);
  }

  private void stubDepartmentMembersIfNeeded(DataScope scope) {
    if (scope == DataScope.DEPARTMENT) {
      when(userQueryService.findActiveUserIdsByDepartmentCodes(TENANT_ID, Set.of("SALES")))
          .thenReturn(Set.of(DEPARTMENT_MEMBER_ID));
    }
  }

  private UUID matchingCreator(DataScope scope) {
    return switch (scope) {
      case OWN -> TARGET_USER_ID;
      case DEPARTMENT -> DEPARTMENT_MEMBER_ID;
      case ORGANIZATION, GLOBAL -> OUTSIDER_ID;
    };
  }

  private SalesOrder order(UUID tenantId, UUID createdBy) {
    SalesOrder order = SalesOrder.builder().build();
    order.setTenantId(tenantId);
    order.setCreatedBy(createdBy);
    return order;
  }
}
