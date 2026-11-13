package com.fabricmanagement.platform.user.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.user.domain.Role;
import com.fabricmanagement.platform.user.domain.RoleScope;
import com.fabricmanagement.platform.user.infra.repository.RoleRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * PLATFORM-BOUNDARY-1: the platform staff role is never listed, assigned or created by a tenant.
 */
class RoleServicePlatformRoleTest {

  private static final UUID TENANT = UUID.fromString("55555555-5555-5555-5555-555555555555");

  private final RoleRepository roleRepository = mock(RoleRepository.class);
  private final RoleService service = new RoleService(roleRepository);

  @BeforeEach
  void bindTenant() {
    TenantContext.setCurrentTenantId(TENANT);
  }

  @AfterEach
  void clearTenant() {
    TenantContext.clear();
  }

  @Test
  @DisplayName("the role list never shows the platform role")
  void listHidesPlatformRole() {
    Role admin = Role.create("Administrator", "ADMIN", "tenant admin");
    Role platform = Role.create("Platform", "PLATFORM_ADMIN", "staff", RoleScope.INTERNAL);
    Role system = Role.create("Other", "OTHER", "system scoped", RoleScope.SYSTEM);
    when(roleRepository.findByTenantIdAndIsActiveTrue(TENANT))
        .thenReturn(List.of(admin, platform, system));

    assertThat(service.findAll()).containsExactly(admin);
  }

  @Test
  @DisplayName("looking up the platform role by id reports it as absent, so it cannot be assigned")
  void lookupHidesPlatformRole() {
    UUID id = UUID.randomUUID();
    when(roleRepository.findByTenantIdAndId(TENANT, id))
        .thenReturn(Optional.of(Role.create("Platform", "PLATFORM_ADMIN", "staff")));

    assertThat(service.findById(id)).isEmpty();
  }

  @Test
  @DisplayName("a tenant cannot create a role with the platform role code")
  void cannotCreatePlatformRoleCode() {
    assertThatThrownBy(() -> service.create("Mine", "platform_admin", "x"))
        .isInstanceOfSatisfying(
            PlatformDomainException.class,
            error -> assertThat(error.getErrorCode()).isEqualTo("USER_ROLE_ALREADY_EXISTS"));
    verify(roleRepository, never()).save(any(Role.class));
  }
}
