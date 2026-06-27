package com.fabricmanagement.platform.tenant.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TenantTest {

  @Test
  @DisplayName("new self-service tenant can remain unactivated before password setup")
  void shouldAllowNullTrialActivationFieldsBeforeActivation() {
    Tenant tenant = Tenant.create("Acme Textiles", "ACME-001");

    assertThat(tenant.getTrialStartedAt()).isNull();
    assertThat(tenant.getLastActivityAt()).isNull();
    assertThat(tenant.getTrialEndsAt()).isNull();
  }

  @Test
  @DisplayName("startTrialNow starts the trial clock and stores effective expiry")
  void shouldStartTrialNow() {
    Tenant tenant = Tenant.create("Acme Textiles", "ACME-001");
    Instant before = Instant.now();

    tenant.startTrialNow(90);

    assertThat(tenant.getStatus()).isEqualTo(TenantStatus.TRIAL);
    assertThat(tenant.getTrialStartedAt()).isBetween(before, Instant.now());
    assertThat(tenant.getLastActivityAt()).isEqualTo(tenant.getTrialStartedAt());
    assertThat(Duration.between(tenant.getTrialStartedAt(), tenant.getTrialEndsAt()).toDays())
        .isEqualTo(90);
  }
}
