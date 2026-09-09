package com.fabricmanagement.common.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.bootstrap.PermissionTemplateSeeder;
import com.fabricmanagement.platform.organization.infra.repository.DepartmentRepository;
import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.platform.user.domain.PermissionTemplate;
import com.fabricmanagement.platform.user.infra.repository.PermissionOverrideRepository;
import com.fabricmanagement.platform.user.infra.repository.PermissionTemplateRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PermissionCatalogueGrantResolutionTest {
  private static final UUID TENANT = UUID.fromString("11111111-1111-4111-8111-111111111111");
  private static final UUID USER = UUID.fromString("22222222-2222-4222-8222-222222222222");
  @Mock private PermissionTemplateRepository templates;
  @Mock private PermissionOverrideRepository overrides;
  @Mock private DepartmentRepository departments;
  @InjectMocks private PermissionEvaluator evaluator;

  @ParameterizedTest
  @CsvSource({
    "MANAGER,FINANCE,costing,manage,ORGANIZATION",
    "WORKER,FINANCE,costing,write,OWN",
    "WORKER,FINANCE,costing,manage,DENIED",
    "SUPERVISOR,WEAVING,production,write,DEPARTMENT",
    "WORKER,WEAVING,production,write,DENIED",
    "MANAGER,SALES,costing,read,DENIED",
    "MANAGER,SALES,production,read,DENIED",
    "MANAGER,WAREHOUSE,logistics,delete,ORGANIZATION",
    "SUPERVISOR,WAREHOUSE,logistics,delete,DENIED",
    "MANAGER,LOGISTICS,logistics,delete,DENIED"
  })
  void realEvaluatorResolvesOnlyApprovedRolesAndScopes(
      String role, String department, String resource, String action, String expected) {
    List<PermissionTemplate> applicable =
        new PermissionTemplateSeeder(null, null)
            .buildDesiredTemplates().stream()
                .filter(row -> role.equals(row.getRoleCode()))
                .filter(
                    row ->
                        row.getDepartmentCode() == null
                            || department.equals(row.getDepartmentCode()))
                .toList();
    when(departments.findAncestorCodes(TENANT, department)).thenReturn(List.of(department));
    when(templates.findEffectiveTemplatesForDepartments(eq(TENANT), eq(role), any()))
        .thenReturn(applicable);
    when(overrides.findActiveOverrides(TENANT, USER)).thenReturn(List.of());
    var result = evaluator.evaluate(TENANT, role, List.of(department), USER);
    assertThat(result.isSuperAdmin()).isFalse();
    if ("DENIED".equals(expected)) {
      assertThat(result.can(resource, action)).isFalse();
      assertThat(result.scopeOf(resource, action)).isNull();
    } else {
      assertThat(result.can(resource, action)).isTrue();
      assertThat(result.scopeOf(resource, action)).isEqualTo(DataScope.valueOf(expected));
    }
  }
}
