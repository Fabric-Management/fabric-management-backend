package com.fabricmanagement.common.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class UidSuffixTest {

  @AfterEach
  void clearTenantContext() {
    TenantContext.clear();
  }

  @Test
  void suffixIsUpperCaseHexOfTheDeclaredLength() {
    assertThat(UidSuffix.random()).matches("[0-9A-F]{" + UidSuffix.LENGTH + "}");
  }

  @Test
  void suffixesDoNotRepeatAcrossAHundredThousandDraws() {
    Set<String> seen = new HashSet<>();
    for (int i = 0; i < 100_000; i++) {
      assertThat(seen.add(UidSuffix.random())).as("draw %d repeated an earlier suffix", i).isTrue();
    }
  }

  @Test
  void generatedUidCarriesTenantPrefixModuleAndFullSuffix() {
    TenantContext.setCurrentTenantUid("ACME-001");

    String uid = new UIDGenerator().generate("MAT", "product");

    assertThat(uid).matches("ACME-001-MAT-[0-9A-F]{" + UidSuffix.LENGTH + "}");
    assertThat(new UIDGenerator().isValid(uid)).isTrue();
  }

  @Test
  void longestUidStillFitsTheHundredCharacterColumn() {
    String tenantUid = "T".repeat(50);

    String uid = tenantUid + "-" + "PROCUREMENT" + "-" + UidSuffix.random();

    assertThat(uid.length()).isLessThanOrEqualTo(100);
  }
}
