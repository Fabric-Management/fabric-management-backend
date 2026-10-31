package com.fabricmanagement.common.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class RequireActiveProfileConfigTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner().withUserConfiguration(RequireActiveProfileConfig.class);

  @Test
  @DisplayName("A context without a profile fails before any bean is created")
  void refusesToStartWithoutAProfile() {
    runner.run(
        context ->
            assertThat(context)
                .hasFailed()
                .getFailure()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No Spring profile is active"));
  }

  @Test
  @DisplayName("A context with a profile starts")
  void startsWhenAProfileIsActive() {
    runner
        .withPropertyValues("spring.profiles.active=test")
        .run(context -> assertThat(context).hasNotFailed());
  }
}
