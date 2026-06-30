package com.fabricmanagement.common.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.dto.PermissionResult;
import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.platform.user.domain.SystemUser;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import com.fabricmanagement.procurement.purchaseorder.domain.PurchaseOrder;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class DataScopeGuardTest {

  private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
  private static final UUID CURRENT_USER_ID =
      UUID.fromString("20000000-0000-0000-0000-000000000001");
  private static final UUID OTHER_USER_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");
  private static final UUID DEPARTMENT_MEMBER_ID =
      UUID.fromString("20000000-0000-0000-0000-000000000003");

  private final PermissionEvaluator permissionEvaluator = mock(PermissionEvaluator.class);
  private final UserRepository userRepository = mock(UserRepository.class);
  private final DataScopeGuard guard =
      new DataScopeGuard(
          permissionEvaluator, new AuthenticatedUserContextResolver(), userRepository);

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
    RequestContextHolder.resetRequestAttributes();
    TenantContext.clear();
  }

  @Test
  void ownScopeAllowsOnlyOwnRecords() {
    authenticate(CURRENT_USER_ID, List.of("PROCUREMENT"));
    when(permissionEvaluator.evaluate(TENANT_ID, "WORKER", List.of("PROCUREMENT"), CURRENT_USER_ID))
        .thenReturn(permissionResult(DataScope.OWN));

    assertThatCode(() -> guard.assertCanAccess("procurement", "read", poCreatedBy(CURRENT_USER_ID)))
        .doesNotThrowAnyException();
    assertThat(guard.canAccess("procurement", "read", poCreatedBy(CURRENT_USER_ID))).isTrue();
    assertThat(guard.canAccess("procurement", "read", poCreatedBy(OTHER_USER_ID))).isFalse();
    assertThatThrownBy(
            () -> guard.assertCanAccess("procurement", "read", poCreatedBy(OTHER_USER_ID)))
        .isInstanceOf(AccessDeniedException.class);
  }

  @Test
  void ownScopeFilterUsesCurrentUserCreatedByPredicate() {
    authenticate(CURRENT_USER_ID, List.of("PROCUREMENT"));
    when(permissionEvaluator.evaluate(TENANT_ID, "WORKER", List.of("PROCUREMENT"), CURRENT_USER_ID))
        .thenReturn(permissionResult(DataScope.OWN));
    CriteriaMocks criteria = criteriaMocks();
    when(criteria.criteriaBuilder.equal(criteria.createdByPath, CURRENT_USER_ID))
        .thenReturn(criteria.expectedPredicate);

    Predicate predicate =
        guard
            .scopeFilter("procurement", "read")
            .toPredicate(criteria.root, criteria.query, criteria.criteriaBuilder);

    assertThat(predicate).isSameAs(criteria.expectedPredicate);
    verify(criteria.root).get("createdBy");
  }

  @Test
  void departmentScopeAllowsDepartmentMembersAndCachesMemberIdsPerRequest() {
    RequestContextHolder.setRequestAttributes(
        new ServletRequestAttributes(new MockHttpServletRequest()));
    authenticate(CURRENT_USER_ID, List.of("PROCUREMENT"));
    when(permissionEvaluator.evaluate(TENANT_ID, "WORKER", List.of("PROCUREMENT"), CURRENT_USER_ID))
        .thenReturn(permissionResult(DataScope.DEPARTMENT));
    when(userRepository.findActiveUserIdsByDepartmentCodes(TENANT_ID, Set.of("PROCUREMENT")))
        .thenReturn(Set.of(DEPARTMENT_MEMBER_ID));

    assertThatCode(
            () -> guard.assertCanAccess("procurement", "write", poCreatedBy(DEPARTMENT_MEMBER_ID)))
        .doesNotThrowAnyException();
    assertThat(guard.canAccess("procurement", "write", poCreatedBy(DEPARTMENT_MEMBER_ID))).isTrue();
    assertThat(guard.canAccess("procurement", "write", poCreatedBy(OTHER_USER_ID))).isFalse();
    assertThatThrownBy(
            () -> guard.assertCanAccess("procurement", "write", poCreatedBy(OTHER_USER_ID)))
        .isInstanceOf(AccessDeniedException.class);

    verify(userRepository).findActiveUserIdsByDepartmentCodes(TENANT_ID, Set.of("PROCUREMENT"));
  }

  @Test
  void organizationAndGlobalScopesUseNoOpListPredicate() {
    authenticate(CURRENT_USER_ID, List.of("PROCUREMENT"));
    when(permissionEvaluator.evaluate(TENANT_ID, "WORKER", List.of("PROCUREMENT"), CURRENT_USER_ID))
        .thenReturn(permissionResult(DataScope.ORGANIZATION), permissionResult(DataScope.GLOBAL));
    CriteriaMocks organizationCriteria = criteriaMocks();
    CriteriaMocks globalCriteria = criteriaMocks();
    when(organizationCriteria.criteriaBuilder.conjunction())
        .thenReturn(organizationCriteria.expectedPredicate);
    when(globalCriteria.criteriaBuilder.conjunction()).thenReturn(globalCriteria.expectedPredicate);

    Predicate organizationPredicate =
        guard
            .scopeFilter("procurement", "read")
            .toPredicate(
                organizationCriteria.root,
                organizationCriteria.query,
                organizationCriteria.criteriaBuilder);
    Predicate globalPredicate =
        guard
            .scopeFilter("procurement", "read")
            .toPredicate(globalCriteria.root, globalCriteria.query, globalCriteria.criteriaBuilder);

    assertThat(organizationPredicate).isSameAs(organizationCriteria.expectedPredicate);
    assertThat(globalPredicate).isSameAs(globalCriteria.expectedPredicate);
    assertThat(guard.canAccess("procurement", "read", poCreatedBy(OTHER_USER_ID))).isTrue();
  }

  @Test
  void managerReadOrganizationWriteDepartmentCanSeeAllButEditOnlyDepartmentRecords() {
    RequestContextHolder.setRequestAttributes(
        new ServletRequestAttributes(new MockHttpServletRequest()));
    authenticate(CURRENT_USER_ID, List.of("PROCUREMENT"));
    PermissionResult managerPermissions =
        new PermissionResult(
            Map.of(
                "procurement",
                Map.of("read", DataScope.ORGANIZATION, "write", DataScope.DEPARTMENT)),
            false);
    when(permissionEvaluator.evaluate(TENANT_ID, "WORKER", List.of("PROCUREMENT"), CURRENT_USER_ID))
        .thenReturn(managerPermissions);
    when(userRepository.findActiveUserIdsByDepartmentCodes(TENANT_ID, Set.of("PROCUREMENT")))
        .thenReturn(Set.of(DEPARTMENT_MEMBER_ID));
    CriteriaMocks criteria = criteriaMocks();
    when(criteria.criteriaBuilder.conjunction()).thenReturn(criteria.expectedPredicate);

    Predicate readPredicate =
        guard
            .scopeFilter("procurement", "read")
            .toPredicate(criteria.root, criteria.query, criteria.criteriaBuilder);

    assertThat(readPredicate).isSameAs(criteria.expectedPredicate);
    assertThat(guard.canAccess("procurement", "write", poCreatedBy(DEPARTMENT_MEMBER_ID))).isTrue();
    assertThat(guard.canAccess("procurement", "write", poCreatedBy(OTHER_USER_ID))).isFalse();
  }

  @Test
  void missingPermissionDeniesEntityAccessAndReturnsImpossibleListPredicate() {
    authenticate(CURRENT_USER_ID, List.of("PROCUREMENT"));
    when(permissionEvaluator.evaluate(TENANT_ID, "WORKER", List.of("PROCUREMENT"), CURRENT_USER_ID))
        .thenReturn(new PermissionResult(Map.of(), false));
    CriteriaMocks criteria = criteriaMocks();
    when(criteria.criteriaBuilder.disjunction()).thenReturn(criteria.expectedPredicate);

    assertThatThrownBy(
            () -> guard.assertCanAccess("procurement", "read", poCreatedBy(CURRENT_USER_ID)))
        .isInstanceOf(AccessDeniedException.class);
    assertThat(guard.canAccess("procurement", "read", poCreatedBy(CURRENT_USER_ID))).isFalse();
    Predicate predicate =
        guard
            .scopeFilter("procurement", "read")
            .toPredicate(criteria.root, criteria.query, criteria.criteriaBuilder);

    assertThat(predicate).isSameAs(criteria.expectedPredicate);
  }

  @Test
  void missingAuthenticationFailsClosedButExplicitSystemContextBypasses() {
    assertThat(guard.canAccess("procurement", "read", poCreatedBy(CURRENT_USER_ID))).isFalse();
    assertThatThrownBy(
            () -> guard.assertCanAccess("procurement", "read", poCreatedBy(CURRENT_USER_ID)))
        .isInstanceOf(AccessDeniedException.class);

    TenantContext.setCurrentTenantId(TENANT_ID);
    TenantContext.setCurrentUserId(SystemUser.ID);

    assertThatCode(() -> guard.assertCanAccess("procurement", "read", poCreatedBy(OTHER_USER_ID)))
        .doesNotThrowAnyException();
    assertThat(guard.canAccess("procurement", "read", poCreatedBy(OTHER_USER_ID))).isTrue();
    verify(permissionEvaluator, never()).evaluate(any(), any(), any(), any());
  }

  @Test
  void anonymousPrincipalMakesCanAccessReturnFalseWithoutThrowing() {
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken("anonymous", "n/a", List.of()));

    assertThat(guard.canAccess("procurement", "read", poCreatedBy(CURRENT_USER_ID))).isFalse();
    verify(permissionEvaluator, never()).evaluate(any(), any(), any(), any());
  }

  @Test
  void systemTenantContextBypassesWithoutAuthentication() {
    TenantContext.setCurrentTenantId(TenantContext.SYSTEM_TENANT_ID);

    assertThatCode(() -> guard.assertCanAccess("procurement", "read", poCreatedBy(OTHER_USER_ID)))
        .doesNotThrowAnyException();
    assertThat(guard.canAccess("procurement", "read", poCreatedBy(OTHER_USER_ID))).isTrue();
    verify(permissionEvaluator, never()).evaluate(any(), any(), any(), any());
  }

  @Test
  void departmentScopeIncludesCurrentUserEvenWhenRepositoryDoesNotReturnThem() {
    authenticate(CURRENT_USER_ID, List.of("PROCUREMENT"));
    when(permissionEvaluator.evaluate(TENANT_ID, "WORKER", List.of("PROCUREMENT"), CURRENT_USER_ID))
        .thenReturn(permissionResult(DataScope.DEPARTMENT));
    when(userRepository.findActiveUserIdsByDepartmentCodes(TENANT_ID, Set.of("PROCUREMENT")))
        .thenReturn(Set.of());

    assertThatCode(() -> guard.assertCanAccess("procurement", "read", poCreatedBy(CURRENT_USER_ID)))
        .doesNotThrowAnyException();
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private CriteriaMocks criteriaMocks() {
    Root<Object> root = mock(Root.class);
    CriteriaQuery<?> query = mock(CriteriaQuery.class);
    CriteriaBuilder criteriaBuilder = mock(CriteriaBuilder.class);
    Path<Object> createdByPath = mock(Path.class);
    Predicate expectedPredicate = mock(Predicate.class);
    when(root.get("createdBy")).thenReturn((Path) createdByPath);
    when(createdByPath.in(any(Collection.class))).thenReturn(expectedPredicate);
    return new CriteriaMocks(root, query, criteriaBuilder, createdByPath, expectedPredicate);
  }

  private void authenticate(UUID userId, List<String> departmentCodes) {
    TenantContext.setCurrentTenantId(TENANT_ID);
    TenantContext.setCurrentUserId(userId);
    AuthenticatedUserContext context =
        new AuthenticatedUserContext(
            userId, "WORKER", departmentCodes, departmentCodes.get(0), TENANT_ID);
    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(context, "n/a", List.of()));
  }

  private PermissionResult permissionResult(DataScope scope) {
    return new PermissionResult(
        Map.of("procurement", Map.of("read", scope, "write", scope)), false);
  }

  private PurchaseOrder poCreatedBy(UUID createdBy) {
    PurchaseOrder po = PurchaseOrder.builder().build();
    po.setCreatedBy(createdBy);
    return po;
  }

  private record CriteriaMocks(
      Root<Object> root,
      CriteriaQuery<?> query,
      CriteriaBuilder criteriaBuilder,
      Path<Object> createdByPath,
      Predicate expectedPredicate) {}
}
