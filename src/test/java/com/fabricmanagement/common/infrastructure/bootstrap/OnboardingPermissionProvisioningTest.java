package com.fabricmanagement.common.infrastructure.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.common.infrastructure.security.PermissionKey;
import com.fabricmanagement.platform.user.domain.DataScope;
import org.junit.jupiter.api.Test;

class OnboardingPermissionProvisioningTest {
  @Test
  void onlyAdministratorRoleReceivesCompanySetupByDefault() {
    var grants =
        new PermissionTemplateSeeder(null, null)
            .buildDesiredTemplates().stream()
                .filter(
                    row ->
                        row.getResource().equals("organization")
                            && row.getAction().equals("configure"))
                .toList();
    assertThat(grants)
        .singleElement()
        .satisfies(
            grant -> {
              assertThat(grant.getRoleCode()).isEqualTo("ADMIN");
              assertThat(grant.getDepartmentCode()).isNull();
              assertThat(grant.getDataScope()).isEqualTo(DataScope.ORGANIZATION);
            });
    assertThat(PermissionKey.of("organization", "configure"))
        .contains(PermissionKey.ORGANIZATION_CONFIGURE);
    assertThat(PermissionKey.ORGANIZATION_CONFIGURE.enforcedBy())
        .isEqualTo(PermissionKey.EnforcedBy.JAVA);
  }
}
