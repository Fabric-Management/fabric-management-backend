package com.fabricmanagement.platform.user.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserTest {

  @Test
  @DisplayName("new non-seeded users default to non-demo-seed")
  void shouldDefaultDemoSeedToFalse() {
    User user = User.create("Owner", "Admin", UUID.randomUUID());

    assertThat(user.isDemoSeed()).isFalse();
  }
}
