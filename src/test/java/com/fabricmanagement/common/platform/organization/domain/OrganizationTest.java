package com.fabricmanagement.common.platform.organization.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Organization")
class OrganizationTest {

  @Test
  @DisplayName("create sets both name and legalName from organizationName")
  void create_setsNameAndLegalName() {
    String organizationName = "ACME Textile Ltd";
    Organization org =
        Organization.create(organizationName, "1234567890", OrganizationType.VERTICAL_MILL);

    assertThat(org.getName()).isEqualTo(organizationName);
    assertThat(org.getLegalName()).isEqualTo(organizationName);
    assertThat(org.getTaxId()).isEqualTo("1234567890");
    assertThat(org.getOrganizationType()).isEqualTo(OrganizationType.VERTICAL_MILL);
  }

  @Test
  @DisplayName("setName with null legalName auto-populates legalName (safeguard)")
  void setName_withNullLegalName_populatesLegalName() {
    Organization org =
        Organization.builder()
            .name("Initial")
            .legalName(null)
            .taxId("111")
            .organizationType(OrganizationType.SPINNER)
            .build();

    org.setName("Updated Name");

    assertThat(org.getName()).isEqualTo("Updated Name");
    assertThat(org.getLegalName()).isEqualTo("Updated Name");
  }

  @Test
  @DisplayName("setName with blank legalName auto-populates legalName (safeguard)")
  void setName_withBlankLegalName_populatesLegalName() {
    Organization org =
        Organization.builder()
            .name("Initial")
            .legalName("   ")
            .taxId("111")
            .organizationType(OrganizationType.WEAVER)
            .build();

    org.setName("New Company");

    assertThat(org.getName()).isEqualTo("New Company");
    assertThat(org.getLegalName()).isEqualTo("New Company");
  }

  @Test
  @DisplayName("setName with existing legalName does not overwrite legalName")
  void setName_withExistingLegalName_doesNotOverwrite() {
    Organization org =
        Organization.builder()
            .name("Display")
            .legalName("Legal Registered Name Inc")
            .taxId("111")
            .organizationType(OrganizationType.VERTICAL_MILL)
            .build();

    org.setName("Updated Display");

    assertThat(org.getName()).isEqualTo("Updated Display");
    assertThat(org.getLegalName()).isEqualTo("Legal Registered Name Inc");
  }

  @Test
  @DisplayName("update uses setName so safeguard applies")
  void update_usesSetName_safeguardApplies() {
    Organization org =
        Organization.builder()
            .name("Old")
            .legalName(null)
            .taxId("111")
            .organizationType(OrganizationType.SPINNER)
            .build();

    org.update("New Name", "222");

    assertThat(org.getName()).isEqualTo("New Name");
    assertThat(org.getLegalName()).isEqualTo("New Name");
    assertThat(org.getTaxId()).isEqualTo("222");
  }
}
