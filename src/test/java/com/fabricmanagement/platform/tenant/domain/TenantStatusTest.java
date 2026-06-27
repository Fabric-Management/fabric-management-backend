package com.fabricmanagement.platform.tenant.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TenantStatusTest {

  @Test
  @DisplayName("TRIAL, ACTIVE, and EXPIRED tenants can login")
  void shouldAllowLoginForReadableStatuses() {
    assertThat(TenantStatus.TRIAL.canLogin()).isTrue();
    assertThat(TenantStatus.ACTIVE.canLogin()).isTrue();
    assertThat(TenantStatus.EXPIRED.canLogin()).isTrue();
    assertThat(TenantStatus.SUSPENDED.canLogin()).isFalse();
    assertThat(TenantStatus.CANCELLED.canLogin()).isFalse();
  }

  @Test
  @DisplayName("only TRIAL and ACTIVE tenants can write")
  void shouldAllowWritesOnlyForTrialAndActive() {
    assertThat(TenantStatus.TRIAL.canWrite()).isTrue();
    assertThat(TenantStatus.ACTIVE.canWrite()).isTrue();
    assertThat(TenantStatus.EXPIRED.canWrite()).isFalse();
    assertThat(TenantStatus.SUSPENDED.canWrite()).isFalse();
    assertThat(TenantStatus.CANCELLED.canWrite()).isFalse();
  }
}
