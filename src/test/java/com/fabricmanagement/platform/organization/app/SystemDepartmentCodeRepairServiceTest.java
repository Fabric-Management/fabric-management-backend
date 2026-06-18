package com.fabricmanagement.platform.organization.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.organization.domain.Department;
import com.fabricmanagement.platform.organization.domain.SystemDepartment;
import com.fabricmanagement.platform.organization.infra.repository.DepartmentRepository;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class SystemDepartmentCodeRepairServiceTest {

  private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
  private static final UUID ORG_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");

  @AfterEach
  void clearTenantContext() {
    TenantContext.clear();
  }

  @Test
  void repairTenantCorrectsLegacyDepartmentCodesWithoutReplacingRows() {
    UUID financeId = UUID.fromString("30000000-0000-0000-0000-000000000001");
    UUID hrId = UUID.fromString("30000000-0000-0000-0000-000000000002");
    UUID qualityId = UUID.fromString("30000000-0000-0000-0000-000000000003");
    List<Department> departments = new ArrayList<>();
    departments.add(department(SystemDepartment.PRODUCTION, null));
    departments.add(department(SystemDepartment.ADMINISTRATION, null));
    departments.add(department(SystemDepartment.LOGISTICS, null));
    departments.add(department(SystemDepartment.UTILITY, null));
    departments.add(department(SystemDepartment.SUPPORT, null));
    departments.add(legacyDepartment(financeId, "Finance & Accounting", "FINANCEACCOUNTING"));
    departments.add(legacyDepartment(hrId, "Human Resources", "HUMANRESOURCES"));
    departments.add(legacyDepartment(qualityId, "Quality Control", "QUALITYCONTROL"));
    List<Department> savedDepartments = new ArrayList<>();
    DepartmentRepository departmentRepository =
        repositoryProxy(
            DepartmentRepository.class,
            (proxy, method, args) -> {
              if ("findByTenantIdAndIsActiveTrue".equals(method.getName())) {
                return departments;
              }
              if ("save".equals(method.getName())) {
                Department saved = (Department) args[0];
                savedDepartments.add(saved);
                return saved;
              }
              return defaultValue(method.getReturnType());
            });
    SystemDepartmentCodeRepairService service =
        new SystemDepartmentCodeRepairService(departmentRepository);

    int repaired = service.repairTenant(TENANT_ID);

    assertThat(repaired).isGreaterThanOrEqualTo(3);
    assertThat(TenantContext.getCurrentTenantIdOrNull()).isNull();
    assertThat(departments)
        .filteredOn(department -> financeId.equals(department.getId()))
        .singleElement()
        .satisfies(
            department -> {
              assertThat(department.getDepartmentCode()).isEqualTo(SystemDepartment.FINANCE.code());
              assertThat(department.getDepartmentName()).isEqualTo("Finance & Accounting");
            });
    assertThat(departments)
        .filteredOn(department -> hrId.equals(department.getId()))
        .singleElement()
        .extracting(Department::getDepartmentCode)
        .isEqualTo(SystemDepartment.HR.code());
    assertThat(departments)
        .filteredOn(department -> qualityId.equals(department.getId()))
        .singleElement()
        .extracting(Department::getDepartmentCode)
        .isEqualTo(SystemDepartment.QUALITY.code());

    assertThat(savedDepartments)
        .anySatisfy(d -> assertThat(d.getDepartmentCode()).isEqualTo("KNITTING"));
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

  private Department department(SystemDepartment systemDepartment, Department parent) {
    Department department =
        Department.create(
            ORG_ID,
            systemDepartment.displayName(),
            systemDepartment.code(),
            systemDepartment.description());
    department.setId(UUID.randomUUID());
    department.setTenantId(TENANT_ID);
    department.setParentDepartment(parent);
    department.setIsSystemDepartment(true);
    return department;
  }

  private Department legacyDepartment(UUID id, String name, String code) {
    Department department = Department.create(ORG_ID, name, code, name);
    department.setId(id);
    department.setTenantId(TENANT_ID);
    department.setIsSystemDepartment(true);
    return department;
  }
}
