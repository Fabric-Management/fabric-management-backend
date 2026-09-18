package com.fabricmanagement.sales.common.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.security.PermissionEvaluator;
import com.fabricmanagement.common.infrastructure.security.dto.PermissionResult;
import com.fabricmanagement.platform.user.app.UserQueryService;
import com.fabricmanagement.platform.user.app.UserQueryService.PermissionIdentity;
import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.sales.common.app.SalesAccessScopeResolver.PermissionFreshness;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SalesAccessScopeResolverTest {

  private static final UUID TENANT = UUID.randomUUID();
  private static final UUID USER = UUID.randomUUID();
  private static final UUID COLLEAGUE = UUID.randomUUID();

  @Mock private PermissionEvaluator permissions;
  @Mock private UserQueryService users;

  private SalesAccessScopeResolver resolver;

  @BeforeEach
  void setUp() {
    resolver = new SalesAccessScopeResolver(permissions, users);
    lenient()
        .when(users.findPermissionIdentity(TENANT, USER))
        .thenReturn(Optional.of(new PermissionIdentity("WORKER", List.of("sales"))));
  }

  @Test
  void ownScopePermitsOnlyTheTargetPrincipal() {
    cachedScope(DataScope.OWN);

    var scope = resolver.resolve(TENANT, USER, "read", PermissionFreshness.CACHED);

    assertThat(scope.permittedPrincipalIds()).containsExactly(USER);
    assertThat(scope.permits(TENANT, TENANT, Set.of(USER))).isTrue();
    assertThat(scope.permits(TENANT, TENANT, Set.of(COLLEAGUE))).isFalse();
  }

  @Test
  void departmentScopeIncludesTargetAndActiveDepartmentMembers() {
    cachedScope(DataScope.DEPARTMENT);
    when(users.findActiveUserIdsByDepartmentCodes(TENANT, Set.of("SALES")))
        .thenReturn(Set.of(COLLEAGUE));

    var scope = resolver.resolve(TENANT, USER, "write", PermissionFreshness.CACHED);

    assertThat(scope.permittedPrincipalIds()).containsExactlyInAnyOrder(USER, COLLEAGUE);
  }

  @Test
  void missingIdentityAndMissingPermissionDeny() {
    assertThat(resolver.resolve(TENANT, null, "read", PermissionFreshness.CACHED).scope()).isNull();
    assertThat(resolver.resolve(null, USER, "read", PermissionFreshness.CACHED).scope()).isNull();
    assertThat(resolver.resolve(TENANT, USER, null, PermissionFreshness.CACHED).scope()).isNull();
    assertThat(
            resolver.resolve(TENANT, UUID.randomUUID(), "read", PermissionFreshness.CACHED).scope())
        .isNull();

    when(permissions.evaluate(TENANT, "WORKER", List.of("sales"), USER))
        .thenReturn(new PermissionResult(Map.of(), false));
    assertThat(resolver.resolve(TENANT, USER, "read", PermissionFreshness.CACHED).scope()).isNull();
  }

  @Test
  void freshResolutionUsesTheFreshEvaluator() {
    when(permissions.evaluateFresh(TENANT, "WORKER", List.of("sales"), USER))
        .thenReturn(
            new PermissionResult(Map.of("sales", Map.of("write", DataScope.GLOBAL)), false));

    var scope = resolver.resolve(TENANT, USER, "write", PermissionFreshness.FRESH);

    assertThat(scope.scope()).isEqualTo(DataScope.GLOBAL);
    assertThat(scope.permits(TENANT, TENANT, Set.of(COLLEAGUE))).isTrue();
    assertThat(scope.permits(UUID.randomUUID(), TENANT, Set.of(COLLEAGUE))).isFalse();
  }

  @Test
  void departmentScopeWithNoDepartmentsStillIncludesTheTargetOnly() {
    when(users.findPermissionIdentity(TENANT, USER))
        .thenReturn(Optional.of(new PermissionIdentity("WORKER", List.of())));
    when(permissions.evaluate(TENANT, "WORKER", List.of(), USER))
        .thenReturn(
            new PermissionResult(Map.of("sales", Map.of("read", DataScope.DEPARTMENT)), false));

    var scope = resolver.resolve(TENANT, USER, "read", PermissionFreshness.CACHED);

    assertThat(scope.permittedPrincipalIds()).containsExactly(USER);
    verify(users, never()).findActiveUserIdsByDepartmentCodes(TENANT, Set.of());
  }

  @Test
  void departmentCodesAreNormalizedTogether() {
    when(users.findPermissionIdentity(TENANT, USER))
        .thenReturn(Optional.of(new PermissionIdentity("WORKER", List.of("sales", "finance"))));
    when(permissions.evaluate(TENANT, "WORKER", List.of("sales", "finance"), USER))
        .thenReturn(
            new PermissionResult(Map.of("sales", Map.of("read", DataScope.DEPARTMENT)), false));
    when(users.findActiveUserIdsByDepartmentCodes(TENANT, Set.of("SALES", "FINANCE")))
        .thenReturn(Set.of(COLLEAGUE));

    var scope = resolver.resolve(TENANT, USER, "read", PermissionFreshness.CACHED);

    assertThat(scope.permittedPrincipalIds()).containsExactlyInAnyOrder(USER, COLLEAGUE);
  }

  private void cachedScope(DataScope scope) {
    when(permissions.evaluate(TENANT, "WORKER", List.of("sales"), USER))
        .thenReturn(
            new PermissionResult(Map.of("sales", Map.of("read", scope, "write", scope)), false));
  }
}
