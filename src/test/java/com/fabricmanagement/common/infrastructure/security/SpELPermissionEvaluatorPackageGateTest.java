package com.fabricmanagement.common.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.security.dto.PermissionResult;
import com.fabricmanagement.common.infrastructure.tenant.PackageAccessPort;
import com.fabricmanagement.platform.user.domain.DataScope;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * PACKAGE-ACCESS-2: the package gate applies on top of role permission, administrators included.
 */
class SpELPermissionEvaluatorPackageGateTest {

  private static final UUID TENANT = UUID.fromString("22222222-2222-2222-2222-222222222222");

  private final PermissionEvaluator permissions = mock(PermissionEvaluator.class);
  private final AuthenticatedUserContextResolver resolver =
      mock(AuthenticatedUserContextResolver.class);
  private final PackageAccessPort packages = mock(PackageAccessPort.class);
  // The two-argument constructor builds an unauthenticated token, which the evaluator refuses
  // before it reaches the role or package checks; the authority makes the token authenticated.
  private final Authentication authentication =
      new TestingAuthenticationToken("user", "n/a", "ROLE_USER");

  @AfterEach
  void clearRequest() {
    RequestContextHolder.resetRequestAttributes();
  }

  @Test
  void anAdministratorIsRefusedAResourceTheTenantPackagesDoNotOpen() {
    grant("ADMIN", new PermissionResult(Map.of(), true));
    when(packages.isResourceOpen(TENANT, "yarn")).thenReturn(false);
    MockHttpServletRequest request = bindRequest();

    assertThat(evaluator().can(authentication, "yarn", "read")).isFalse();
    assertThat(PackageAccessDenial.resourceIn(request)).contains("yarn");
  }

  @Test
  void aMemberWithRolePermissionIsStillRefusedWhenThePackageIsNotEnabled() {
    grant("MANAGER", new PermissionResult(Map.of("yarn", Map.of("read", DataScope.GLOBAL)), false));
    when(packages.isResourceOpen(TENANT, "yarn")).thenReturn(false);

    assertThat(evaluator().can(authentication, "yarn", "read")).isFalse();
    assertThat(evaluator().hasScope(authentication, "yarn", "read", "ORGANIZATION")).isFalse();
  }

  @Test
  void anEnabledPackageStillNeedsTheRolePermission() {
    grant("VIEWER", new PermissionResult(Map.of(), false));
    when(packages.isResourceOpen(TENANT, "yarn")).thenReturn(true);

    assertThat(evaluator().can(authentication, "yarn", "read")).isFalse();
  }

  @Test
  void anEnabledPackageAndARolePermissionTogetherGrantAccess() {
    grant("MANAGER", new PermissionResult(Map.of("yarn", Map.of("read", DataScope.GLOBAL)), false));
    when(packages.isResourceOpen(TENANT, "yarn")).thenReturn(true);
    MockHttpServletRequest request = bindRequest();

    assertThat(evaluator().can(authentication, "yarn", "read")).isTrue();
    assertThat(evaluator().hasScope(authentication, "yarn", "read", "ORGANIZATION")).isTrue();
    assertThat(PackageAccessDenial.resourceIn(request)).isEmpty();
  }

  @Test
  void platformStaffAreNotBoundByATenantsPackages() {
    grant("PLATFORM_ADMIN", new PermissionResult(Map.of(), true));
    when(packages.isResourceOpen(any(), any())).thenReturn(false);

    assertThat(evaluator().can(authentication, "yarn", "read")).isTrue();
  }

  @Test
  void withoutAPackageCatalogueEveryResourceStaysOpenForRoleChecks() {
    grant("ADMIN", new PermissionResult(Map.of(), true));
    var roleOnly = new SpELPermissionEvaluator(permissions, resolver);

    assertThat(roleOnly.can(authentication, "yarn", "read")).isTrue();
  }

  private SpELPermissionEvaluator evaluator() {
    return new SpELPermissionEvaluator(permissions, resolver, Optional.of(packages));
  }

  private void grant(String role, PermissionResult result) {
    var context =
        new AuthenticatedUserContext(UUID.randomUUID(), role, List.of(), null, TENANT, false, null);
    when(resolver.resolve(authentication)).thenReturn(Optional.of(context));
    when(permissions.evaluate(TENANT, role, List.of(), context.userId())).thenReturn(result);
  }

  private static MockHttpServletRequest bindRequest() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    return request;
  }
}
