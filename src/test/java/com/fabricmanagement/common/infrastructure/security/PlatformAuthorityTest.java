package com.fabricmanagement.common.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** PLATFORM-BOUNDARY-1: a role code alone never makes platform staff. */
class PlatformAuthorityTest {

  private static final UUID PLATFORM = TenantContext.SYSTEM_TENANT_ID;
  private static final UUID CUSTOMER = UUID.fromString("33333333-3333-3333-3333-333333333333");

  @Test
  @DisplayName("platform staff: the platform role in the platform tenant")
  void platformRoleInPlatformTenant() {
    assertThat(PlatformAuthority.isPlatformAdmin("PLATFORM_ADMIN", PLATFORM)).isTrue();
    assertThat(PlatformAuthority.isPlatformAdmin("platform_admin", PLATFORM)).isTrue();
  }

  @Test
  @DisplayName("the platform role in a customer, demo or Playground tenant is not platform staff")
  void platformRoleElsewhere() {
    assertThat(PlatformAuthority.isPlatformAdmin("PLATFORM_ADMIN", CUSTOMER)).isFalse();
    assertThat(PlatformAuthority.isPlatformAdmin("PLATFORM_ADMIN", null)).isFalse();
    assertThat(PlatformAuthority.isPlatformAdmin("ADMIN", PLATFORM)).isFalse();
  }

  @Test
  @DisplayName("a Playground session is never platform staff, even in the platform tenant")
  void playgroundContext() {
    var playground =
        new AuthenticatedUserContext(
            UUID.randomUUID(), "PLATFORM_ADMIN", List.of(), null, PLATFORM, true, "guest");
    var real =
        new AuthenticatedUserContext(
            UUID.randomUUID(), "PLATFORM_ADMIN", List.of(), null, PLATFORM, false, null);

    assertThat(PlatformAuthority.isPlatformAdmin(playground)).isFalse();
    assertThat(PlatformAuthority.isPlatformAdmin(real)).isTrue();
    assertThat(PlatformAuthority.isPlatformAdmin((AuthenticatedUserContext) null)).isFalse();
  }

  @Test
  @DisplayName("effective role: other roles pass unchanged, the platform role only where it counts")
  void effectiveRole() {
    assertThat(PlatformAuthority.effectiveRoleCode("ADMIN", CUSTOMER, false, false))
        .isEqualTo("ADMIN");
    assertThat(PlatformAuthority.effectiveRoleCode("MANAGER", CUSTOMER, true, true))
        .isEqualTo("MANAGER");
    assertThat(PlatformAuthority.effectiveRoleCode(null, PLATFORM, false, false)).isNull();

    assertThat(PlatformAuthority.effectiveRoleCode("PLATFORM_ADMIN", PLATFORM, false, false))
        .isEqualTo("PLATFORM_ADMIN");
    assertThat(PlatformAuthority.effectiveRoleCode("PLATFORM_ADMIN", CUSTOMER, false, false))
        .isNull();
    assertThat(PlatformAuthority.effectiveRoleCode("PLATFORM_ADMIN", PLATFORM, true, false))
        .isNull();
    assertThat(PlatformAuthority.effectiveRoleCode("PLATFORM_ADMIN", PLATFORM, false, true))
        .isNull();
  }
}
