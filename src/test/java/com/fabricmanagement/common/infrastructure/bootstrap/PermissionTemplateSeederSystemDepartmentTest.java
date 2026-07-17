package com.fabricmanagement.common.infrastructure.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.organization.domain.SystemDepartment;
import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.platform.user.domain.PermissionTemplate;
import com.fabricmanagement.platform.user.infra.repository.PermissionTemplateRepository;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class PermissionTemplateSeederSystemDepartmentTest {

  @AfterEach
  void clearTenantContext() {
    TenantContext.clear();
  }

  @Test
  @SuppressWarnings("unchecked")
  void seededPermissionTemplateDepartmentCodesMatchCanonicalPermissionBackedDepartments() {
    List<PermissionTemplate> savedTemplates = new ArrayList<>();
    PermissionTemplateRepository permissionTemplateRepository =
        repositoryProxy(
            PermissionTemplateRepository.class,
            (proxy, method, args) -> {
              if ("saveAll".equals(method.getName())) {
                List<PermissionTemplate> templates = (List<PermissionTemplate>) args[0];
                savedTemplates.addAll(templates);
                return templates;
              }
              if ("existsByTenantId".equals(method.getName())) {
                return false;
              }
              if ("findByTenantId".equals(method.getName())) {
                return List.of();
              }
              return defaultValue(method.getReturnType());
            });
    PermissionTemplateSeeder seeder =
        new PermissionTemplateSeeder(permissionTemplateRepository, transactionTemplate());

    seeder.seed();

    assertThat(TenantContext.getCurrentTenantIdOrNull()).isNull();
    assertThat(
            savedTemplates.stream()
                .map(PermissionTemplate::getDepartmentCode)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet()))
        .containsExactlyInAnyOrderElementsOf(SystemDepartment.permissionBackedCodes());
    assertThat(savedTemplates)
        .filteredOn(
            template -> SystemDepartment.PROCUREMENT.code().equals(template.getDepartmentCode()))
        .extracting(
            PermissionTemplate::getRoleCode,
            PermissionTemplate::getResource,
            PermissionTemplate::getAction,
            PermissionTemplate::getDataScope)
        .contains(
            org.assertj.core.groups.Tuple.tuple("WORKER", "products", "read", DataScope.OWN),
            org.assertj.core.groups.Tuple.tuple(
                "SUPERVISOR", "products", "read", DataScope.DEPARTMENT),
            org.assertj.core.groups.Tuple.tuple(
                "SUPERVISOR", "products", "write", DataScope.DEPARTMENT),
            org.assertj.core.groups.Tuple.tuple(
                "MANAGER", "products", "read", DataScope.ORGANIZATION),
            org.assertj.core.groups.Tuple.tuple(
                "MANAGER", "products", "write", DataScope.DEPARTMENT));

    assertThat(savedTemplates)
        .filteredOn(
            template -> SystemDepartment.PROCUREMENT.code().equals(template.getDepartmentCode()))
        .extracting(
            PermissionTemplate::getRoleCode,
            PermissionTemplate::getResource,
            PermissionTemplate::getAction,
            PermissionTemplate::getDataScope)
        .contains(
            org.assertj.core.groups.Tuple.tuple("WORKER", "colors", "read", DataScope.ORGANIZATION),
            org.assertj.core.groups.Tuple.tuple(
                "MANAGER", "colors", "read", DataScope.ORGANIZATION))
        .doesNotContain(
            org.assertj.core.groups.Tuple.tuple(
                "SUPERVISOR", "colors", "write", DataScope.ORGANIZATION),
            org.assertj.core.groups.Tuple.tuple(
                "MANAGER", "colors", "write", DataScope.ORGANIZATION));
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

  private TransactionTemplate transactionTemplate() {
    return new TransactionTemplate(
        new PlatformTransactionManager() {
          @Override
          public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
          }

          @Override
          public void commit(TransactionStatus status) {}

          @Override
          public void rollback(TransactionStatus status) {}
        });
  }
}
