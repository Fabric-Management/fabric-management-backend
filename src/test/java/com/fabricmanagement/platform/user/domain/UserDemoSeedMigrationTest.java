package com.fabricmanagement.platform.user.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserDemoSeedMigrationTest {

  @Test
  @DisplayName("demo_seed migration adds a non-null false default")
  void shouldAddDemoSeedWithDefaultFalse() throws Exception {
    String migration =
        Files.readString(
            Path.of("src/main/resources/db/migration/V20260627110000__add_user_demo_seed.sql"));

    assertThat(migration)
        .contains("ADD COLUMN IF NOT EXISTS demo_seed BOOLEAN NOT NULL DEFAULT FALSE");
  }
}
