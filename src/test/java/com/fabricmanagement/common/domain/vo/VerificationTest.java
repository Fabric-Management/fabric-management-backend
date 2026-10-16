package com.fabricmanagement.common.domain.vo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The value kept on a partner, an address card and an order delivery (CONSIGNEE-1 C5). */
class VerificationTest {

  @Test
  void unverifiedNamesNobodyAndNoTime() {
    Verification value = Verification.unverified();

    assertThat(value.isVerified()).isFalse();
    assertThat(value.getStatus()).isEqualTo(DeliveryVerificationStatus.UNVERIFIED);
    assertThat(value.getVerifiedBy()).isNull();
    assertThat(value.getVerifiedAt()).isNull();
  }

  @Test
  void verifiedByKeepsWhoAndWhen() {
    UUID who = UUID.randomUUID();
    Instant when = Instant.parse("2026-10-16T09:00:00Z");

    Verification value = Verification.verifiedBy(who, when);

    assertThat(value.isVerified()).isTrue();
    assertThat(value.getVerifiedBy()).isEqualTo(who);
    assertThat(value.getVerifiedAt()).isEqualTo(when);
  }

  @Test
  void aRecordThatPredatesTheRuleIsVerifiedWithNobodyToName() {
    Verification value = Verification.predating();

    assertThat(value.isVerified()).isTrue();
    assertThat(value.getVerifiedBy()).isNull();
    assertThat(value.getVerifiedAt()).isNull();
  }
}
