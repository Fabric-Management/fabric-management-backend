package com.fabricmanagement.common.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.security.dto.PermissionResult;
import com.fabricmanagement.platform.organization.infra.repository.DepartmentRepository;
import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.platform.user.domain.PermissionOverride;
import com.fabricmanagement.platform.user.domain.PermissionTemplate;
import com.fabricmanagement.platform.user.infra.repository.PermissionOverrideRepository;
import com.fabricmanagement.platform.user.infra.repository.PermissionTemplateRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OwnershipAssignmentPermissionEvaluatorTest {

  private static final UUID TENANT_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
  private static final UUID USER_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");

  @Mock private PermissionTemplateRepository templateRepository;
  @Mock private PermissionOverrideRepository overrideRepository;
  @Mock private DepartmentRepository departmentRepository;

  private PermissionEvaluator evaluator;

  @BeforeEach
  void setUp() {
    evaluator =
        new PermissionEvaluator(templateRepository, overrideRepository, departmentRepository);
    when(departmentRepository.findAncestorCodes(TENANT_ID, "SALES")).thenReturn(List.of("SALES"));
  }

  @Test
  void managerRoleWithoutAssignOwnerGrantIsDenied() {
    stubTemplates("MANAGER", List.of());
    when(overrideRepository.findActiveOverrides(TENANT_ID, USER_ID)).thenReturn(List.of());

    PermissionResult result = evaluator.evaluate(TENANT_ID, "MANAGER", List.of("SALES"), USER_ID);

    assertThat(result.can("sales", "assign-owner")).isFalse();
  }

  @Test
  void nonManagerUserOverrideCanGrantAssignOwner() {
    PermissionOverride override =
        PermissionOverride.builder()
            .userId(USER_ID)
            .resource("sales")
            .action("assign-owner")
            .dataScope(DataScope.ORGANIZATION)
            .grantedBy(UUID.randomUUID())
            .build();
    stubTemplates("SALES_COORDINATOR", List.of());
    when(overrideRepository.findActiveOverrides(TENANT_ID, USER_ID)).thenReturn(List.of(override));

    PermissionResult result =
        evaluator.evaluate(TENANT_ID, "SALES_COORDINATOR", List.of("SALES"), USER_ID);

    assertThat(result.can("sales", "assign-owner")).isTrue();
    assertThat(result.scopeOf("sales", "assign-owner")).isEqualTo(DataScope.ORGANIZATION);
  }

  @Test
  void salesWriteDoesNotImplyAssignOwner() {
    PermissionTemplate salesWrite =
        PermissionTemplate.builder()
            .roleCode("WORKER")
            .departmentCode("SALES")
            .resource("sales")
            .action("write")
            .dataScope(DataScope.OWN)
            .build();
    stubTemplates("WORKER", List.of(salesWrite));
    when(overrideRepository.findActiveOverrides(TENANT_ID, USER_ID)).thenReturn(List.of());

    PermissionResult result = evaluator.evaluate(TENANT_ID, "WORKER", List.of("SALES"), USER_ID);

    assertThat(result.can("sales", "write")).isTrue();
    assertThat(result.can("sales", "assign-owner")).isFalse();
  }

  private void stubTemplates(String roleCode, List<PermissionTemplate> templates) {
    when(templateRepository.findEffectiveTemplatesForDepartments(
            TENANT_ID, roleCode, List.of("SALES")))
        .thenReturn(templates);
  }
}
