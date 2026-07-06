package com.fabricmanagement.platform.organization;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class OrganizationPreferredCurrencyMigrationTest {

  @Test
  void migrationAddsPreferredCurrencyColumnAndConstraint() throws Exception {
    String migration =
        Files.readString(
            Path.of(
                "src/main/resources/db/migration/"
                    + "V20260705120000__organization_preferred_currency.sql"));

    assertThat(migration).contains("preferred_currency varchar(3)");
    assertThat(migration).contains("chk_common_organization_preferred_currency");
    assertThat(migration).contains("preferred_currency IS NULL");
  }
}
