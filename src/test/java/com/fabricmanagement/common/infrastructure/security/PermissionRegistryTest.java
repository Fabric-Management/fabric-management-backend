package com.fabricmanagement.common.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** COLOR-RBAC-1: {@code colors} must be a valid resource without widening the action catalogue. */
class PermissionRegistryTest {

  @Test
  void colorsIsAValidResource() {
    assertThat(PermissionRegistry.isValidResource("colors")).isTrue();
  }

  @Test
  void theFourColourActionsRemainValid() {
    assertThat(PermissionRegistry.isValidAction("read")).isTrue();
    assertThat(PermissionRegistry.isValidAction("write")).isTrue();
    assertThat(PermissionRegistry.isValidAction("approve")).isTrue();
    assertThat(PermissionRegistry.isValidAction("manage")).isTrue();
  }

  @Test
  void unknownResourceOrActionStaysRejected() {
    assertThat(PermissionRegistry.isValidResource("colours")).isFalse();
    assertThat(PermissionRegistry.isValidResource("color")).isFalse();
    assertThat(PermissionRegistry.isValidResource(null)).isFalse();
    assertThat(PermissionRegistry.isValidAction("paint")).isFalse();
    assertThat(PermissionRegistry.isValidAction(null)).isFalse();
  }
}
