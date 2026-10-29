package com.fabricmanagement.common.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TenantContextExecuteTest {

  private static final UUID TENANT_A = UUID.fromString("a0000000-0000-0000-0000-000000000001");
  private static final UUID TENANT_B = UUID.fromString("b0000000-0000-0000-0000-000000000002");
  private static final UUID USER = UUID.fromString("c0000000-0000-0000-0000-000000000003");

  @AfterEach
  void clearTenantContext() {
    TenantContext.clear();
  }

  private static void bindTenantA() {
    TenantContext.setCurrentTenantId(TENANT_A);
    TenantContext.setCurrentTenantUid("AAA-001");
    TenantContext.setCurrentTenantCountry("TR");
    TenantContext.setCurrentUserId(USER);
  }

  @Test
  void enteringAnotherTenantDropsTheFormerTenantsUidAndCountry() {
    bindTenantA();
    AtomicReference<String> uid = new AtomicReference<>("unset");
    AtomicReference<String> country = new AtomicReference<>("unset");
    AtomicReference<UUID> user = new AtomicReference<>();

    TenantContext.executeInTenantContext(
        TENANT_B,
        () -> {
          uid.set(TenantContext.getCurrentTenantUid());
          country.set(TenantContext.getCurrentTenantCountry());
          user.set(TenantContext.getCurrentUserId());
        });

    assertThat(uid.get()).isNull();
    assertThat(country.get()).isNull();
    assertThat(user.get()).as("the same person keeps acting").isEqualTo(USER);
  }

  @Test
  void supplierOverloadDropsThemToo() {
    bindTenantA();

    String uid = TenantContext.executeInTenantContext(TENANT_B, TenantContext::getCurrentTenantUid);

    assertThat(uid).isNull();
  }

  @Test
  void stayingInTheSameTenantKeepsUidAndCountry() {
    bindTenantA();

    String uid = TenantContext.executeInTenantContext(TENANT_A, TenantContext::getCurrentTenantUid);
    String country =
        TenantContext.executeInTenantContext(TENANT_A, TenantContext::getCurrentTenantCountry);

    assertThat(uid).isEqualTo("AAA-001");
    assertThat(country).isEqualTo("TR");
  }

  @Test
  void everythingIsRestoredAfterwards() {
    bindTenantA();

    TenantContext.executeInTenantContext(TENANT_B, () -> TenantContext.getCurrentTenantUid());

    assertThat(TenantContext.getCurrentTenantIdOrNull()).isEqualTo(TENANT_A);
    assertThat(TenantContext.getCurrentTenantUid()).isEqualTo("AAA-001");
    assertThat(TenantContext.getCurrentTenantCountry()).isEqualTo("TR");
  }

  @Test
  void everythingIsRestoredWhenTheBodyThrows() {
    bindTenantA();

    assertThatThrownBy(
            () ->
                TenantContext.executeInTenantContext(
                    TENANT_B,
                    () -> {
                      throw new IllegalStateException("boom");
                    }))
        .isInstanceOf(IllegalStateException.class);

    assertThat(TenantContext.getCurrentTenantIdOrNull()).isEqualTo(TENANT_A);
    assertThat(TenantContext.getCurrentTenantUid()).isEqualTo("AAA-001");
    assertThat(TenantContext.getCurrentTenantCountry()).isEqualTo("TR");
  }

  @Test
  void overloadWithUidSetsItAndStillDropsTheFormerCountry() {
    bindTenantA();
    AtomicReference<String> country = new AtomicReference<>("unset");

    String uid =
        TenantContext.executeInTenantContext(
            TENANT_B,
            "BBB-002",
            () -> {
              country.set(TenantContext.getCurrentTenantCountry());
              return TenantContext.getCurrentTenantUid();
            });

    assertThat(uid).isEqualTo("BBB-002");
    assertThat(country.get()).isNull();
    assertThat(TenantContext.getCurrentTenantUid()).isEqualTo("AAA-001");
  }

  @Test
  void aNullTenantIsStillRefused() {
    assertThatThrownBy(() -> TenantContext.executeInTenantContext(null, () -> "x"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
