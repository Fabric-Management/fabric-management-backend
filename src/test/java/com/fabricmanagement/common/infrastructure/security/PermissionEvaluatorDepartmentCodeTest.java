package com.fabricmanagement.common.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.common.infrastructure.security.dto.PermissionResult;
import com.fabricmanagement.platform.organization.domain.SystemDepartment;
import com.fabricmanagement.platform.organization.infra.repository.DepartmentRepository;
import com.fabricmanagement.platform.user.domain.DataScope;
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

class PermissionEvaluatorDepartmentCodeTest {

  private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
  private static final UUID USER_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");

  @ParameterizedTest
  @MethodSource("departmentPermissions")
  void evaluatesDepartmentTemplatesWithCanonicalCodes(
      SystemDepartment department, String resource, String action) {
    PermissionTemplate template =
        PermissionTemplate.builder()
            .roleCode("WORKER")
            .departmentCode(department.code())
            .resource(resource)
            .action(action)
            .dataScope(DataScope.OWN)
            .build();
    DepartmentRepository departmentRepository =
        repositoryProxy(
            DepartmentRepository.class,
            (proxy, method, args) ->
                "findAncestorCodes".equals(method.getName())
                    ? List.of(department.code())
                    : defaultValue(method.getReturnType()));
    PermissionTemplateRepository templateRepository =
        repositoryProxy(
            PermissionTemplateRepository.class,
            (proxy, method, args) ->
                "findEffectiveTemplatesForDepartments".equals(method.getName())
                    ? List.of(template)
                    : defaultValue(method.getReturnType()));
    PermissionOverrideRepository overrideRepository =
        repositoryProxy(
            PermissionOverrideRepository.class,
            (proxy, method, args) ->
                "findActiveOverrides".equals(method.getName())
                    ? List.of()
                    : defaultValue(method.getReturnType()));
    PermissionEvaluator evaluator =
        new PermissionEvaluator(templateRepository, overrideRepository, departmentRepository);

    PermissionResult result =
        evaluator.evaluate(TENANT_ID, "WORKER", List.of(department.code()), USER_ID);

    assertThat(result.can(resource, action)).isTrue();
    assertThat(result.scopeOf(resource, action)).isEqualTo(DataScope.OWN);
  }

  private static Stream<Arguments> departmentPermissions() {
    return Stream.of(
        Arguments.of(SystemDepartment.FINANCE, "finance", "read"),
        Arguments.of(SystemDepartment.HR, "members", "read"),
        Arguments.of(SystemDepartment.QUALITY, "products", "read"));
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
