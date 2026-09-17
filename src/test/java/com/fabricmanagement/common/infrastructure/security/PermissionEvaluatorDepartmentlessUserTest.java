package com.fabricmanagement.common.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.common.infrastructure.security.dto.PermissionResult;
import com.fabricmanagement.platform.organization.infra.repository.DepartmentRepository;
import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.platform.user.domain.PermissionOverride;
import com.fabricmanagement.platform.user.domain.PermissionTemplate;
import com.fabricmanagement.platform.user.infra.repository.PermissionOverrideRepository;
import com.fabricmanagement.platform.user.infra.repository.PermissionTemplateRepository;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class PermissionEvaluatorDepartmentlessUserTest {

  private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
  private static final UUID USER_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
  private static final String ROLE_CODE = "WORKER";

  @ParameterizedTest
  @MethodSource("departmentlessDepartmentCodes")
  void grantsGlobalTemplateWithItsScope(List<String> departmentCodes) {
    PermissionTemplate template = template(null, "sales-orders", "read", DataScope.ORGANIZATION);

    PermissionResult result =
        evaluator(List.of(template), List.of())
            .evaluate(TENANT_ID, ROLE_CODE, departmentCodes, USER_ID);

    assertThat(result.can("sales-orders", "read")).isTrue();
    assertThat(result.scopeOf("sales-orders", "read")).isEqualTo(DataScope.ORGANIZATION);
    assertThat(result.isSuperAdmin()).isFalse();
  }

  @ParameterizedTest
  @MethodSource("departmentlessDepartmentCodes")
  void doesNotGrantDepartmentScopedTemplate(List<String> departmentCodes) {
    PermissionTemplate template = template("FINANCE", "finance", "read", DataScope.DEPARTMENT);

    PermissionResult result =
        evaluator(List.of(template), List.of())
            .evaluate(TENANT_ID, ROLE_CODE, departmentCodes, USER_ID);

    assertThat(result.can("finance", "read")).isFalse();
    assertThat(result.scopeOf("finance", "read")).isNull();
    assertThat(result.isSuperAdmin()).isFalse();
  }

  @ParameterizedTest
  @MethodSource("departmentlessDepartmentCodes")
  void doesNotLookUpDepartmentAncestors(List<String> departmentCodes) {
    DepartmentRepository departmentRepository =
        repositoryProxy(
            DepartmentRepository.class,
            (proxy, method, args) -> {
              if ("findAncestorCodes".equals(method.getName())) {
                throw new AssertionError("findAncestorCodes must not be invoked");
              }
              return defaultValue(method.getReturnType());
            });

    PermissionResult result =
        evaluator(List.of(), List.of(), departmentRepository)
            .evaluate(TENANT_ID, ROLE_CODE, departmentCodes, USER_ID);

    assertThat(result.can("finance", "read")).isFalse();
    assertThat(result.scopeOf("finance", "read")).isNull();
    assertThat(result.isSuperAdmin()).isFalse();
  }

  @ParameterizedTest
  @MethodSource("departmentlessDepartmentCodes")
  void nullScopeOverrideRevokesGlobalTemplate(List<String> departmentCodes) {
    PermissionTemplate template = template(null, "finance", "read", DataScope.ORGANIZATION);
    PermissionOverride override = override("finance", "read", null);

    PermissionResult result =
        evaluator(List.of(template), List.of(override))
            .evaluate(TENANT_ID, ROLE_CODE, departmentCodes, USER_ID);

    assertThat(result.can("finance", "read")).isFalse();
    assertThat(result.scopeOf("finance", "read")).isNull();
    assertThat(result.isSuperAdmin()).isFalse();
  }

  @ParameterizedTest
  @MethodSource("departmentlessDepartmentCodes")
  void scopedOverridesGrantAndReplacePermissions(List<String> departmentCodes) {
    PermissionTemplate template = template(null, "finance", "read", DataScope.OWN);
    PermissionOverride grant = override("reports", "export", DataScope.DEPARTMENT);
    PermissionOverride replacement = override("finance", "read", DataScope.GLOBAL);

    PermissionResult result =
        evaluator(List.of(template), List.of(grant, replacement))
            .evaluate(TENANT_ID, ROLE_CODE, departmentCodes, USER_ID);

    assertThat(result.can("reports", "export")).isTrue();
    assertThat(result.scopeOf("reports", "export")).isEqualTo(DataScope.DEPARTMENT);
    assertThat(result.can("finance", "read")).isTrue();
    assertThat(result.scopeOf("finance", "read")).isEqualTo(DataScope.GLOBAL);
    assertThat(result.isSuperAdmin()).isFalse();
  }

  @ParameterizedTest
  @MethodSource("departmentlessDepartmentCodes")
  void returnsEmptyResultWithoutTemplatesOrOverrides(List<String> departmentCodes) {
    PermissionResult result =
        evaluator(List.of(), List.of()).evaluate(TENANT_ID, ROLE_CODE, departmentCodes, USER_ID);

    assertThat(result.permissions()).isEmpty();
    assertThat(result.can("finance", "read")).isFalse();
    assertThat(result.scopeOf("finance", "read")).isNull();
    assertThat(result.isSuperAdmin()).isFalse();
  }

  @ParameterizedTest
  @MethodSource("departmentlessDepartmentCodes")
  void preservesNullAndSuperAdminRoleFastPaths(List<String> departmentCodes) {
    PermissionEvaluator evaluator = evaluator(List.of(), List.of());

    PermissionResult nullRoleResult = evaluator.evaluate(TENANT_ID, null, departmentCodes, USER_ID);
    PermissionResult adminResult = evaluator.evaluate(TENANT_ID, "ADMIN", departmentCodes, USER_ID);
    PermissionResult platformAdminResult =
        evaluator.evaluate(TENANT_ID, "PLATFORM_ADMIN", departmentCodes, USER_ID);

    assertThat(nullRoleResult.permissions()).isEmpty();
    assertThat(nullRoleResult.can("finance", "read")).isFalse();
    assertThat(nullRoleResult.scopeOf("finance", "read")).isNull();
    assertThat(nullRoleResult.isSuperAdmin()).isFalse();

    assertThat(adminResult.can("finance", "read")).isTrue();
    assertThat(adminResult.scopeOf("finance", "read")).isEqualTo(DataScope.GLOBAL);
    assertThat(adminResult.isSuperAdmin()).isTrue();

    assertThat(platformAdminResult.can("finance", "read")).isTrue();
    assertThat(platformAdminResult.scopeOf("finance", "read")).isEqualTo(DataScope.GLOBAL);
    assertThat(platformAdminResult.isSuperAdmin()).isTrue();
  }

  private static Stream<Arguments> departmentlessDepartmentCodes() {
    return Stream.of(Arguments.of((Object) null), Arguments.of(List.of()));
  }

  private PermissionEvaluator evaluator(
      List<PermissionTemplate> templates, List<PermissionOverride> overrides) {
    DepartmentRepository departmentRepository =
        repositoryProxy(
            DepartmentRepository.class,
            (proxy, method, args) -> defaultValue(method.getReturnType()));
    return evaluator(templates, overrides, departmentRepository);
  }

  private PermissionEvaluator evaluator(
      List<PermissionTemplate> templates,
      List<PermissionOverride> overrides,
      DepartmentRepository departmentRepository) {
    PermissionTemplateRepository templateRepository =
        repositoryProxy(
            PermissionTemplateRepository.class,
            (proxy, method, args) ->
                "findEffectiveTemplatesForDepartments".equals(method.getName())
                    ? templates
                    : defaultValue(method.getReturnType()));
    PermissionOverrideRepository overrideRepository =
        repositoryProxy(
            PermissionOverrideRepository.class,
            (proxy, method, args) ->
                "findActiveOverrides".equals(method.getName())
                    ? overrides
                    : defaultValue(method.getReturnType()));
    return new PermissionEvaluator(templateRepository, overrideRepository, departmentRepository);
  }

  private PermissionTemplate template(
      String departmentCode, String resource, String action, DataScope dataScope) {
    return PermissionTemplate.builder()
        .roleCode(ROLE_CODE)
        .departmentCode(departmentCode)
        .resource(resource)
        .action(action)
        .dataScope(dataScope)
        .build();
  }

  private PermissionOverride override(String resource, String action, DataScope dataScope) {
    return PermissionOverride.builder()
        .userId(USER_ID)
        .resource(resource)
        .action(action)
        .dataScope(dataScope)
        .build();
  }

  @SuppressWarnings("unchecked")
  private <T> T repositoryProxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
    return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
  }

  private Object defaultValue(Class<?> returnType) {
    if (returnType == boolean.class) {
      return false;
    }
    if (returnType == int.class || returnType == long.class) {
      return 0;
    }
    return null;
  }
}
