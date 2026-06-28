package com.fabricmanagement.platform.tenant.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.tenant.dto.GoRealResponse;
import com.fabricmanagement.platform.tenant.dto.TenantDto;
import com.fabricmanagement.platform.user.app.UserOnboardingService;
import com.fabricmanagement.platform.user.domain.Role;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.dto.CompleteOnboardingRequest;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TenantGoRealServiceTest {

  private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

  @Mock private TenantSystemService tenantSystemService;
  @Mock private UserRepository userRepository;
  @Mock private UserOnboardingService userOnboardingService;
  @Mock private TenantTransactionalPurgeService purgeService;

  private TenantGoRealService service;

  @BeforeEach
  void setUp() {
    service =
        new TenantGoRealService(
            tenantSystemService, userRepository, userOnboardingService, purgeService);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void shouldConfirmDetailsThenPurgeForDemoModeOwner() {
    CompleteOnboardingRequest request =
        CompleteOnboardingRequest.builder().organizationName("Acme Real Ltd").build();
    Instant startedAt = Instant.parse("2026-06-27T12:00:00Z");
    Instant endsAt = Instant.parse("2026-09-25T12:00:00Z");
    when(tenantSystemService.findById(TENANT_ID))
        .thenReturn(Optional.of(TenantDto.builder().id(TENANT_ID).demoMode(true).build()));
    when(userRepository.findByTenantIdAndId(TENANT_ID, USER_ID)).thenReturn(Optional.of(owner()));
    when(purgeService.goReal(TENANT_ID))
        .thenReturn(
            new TenantTransactionalPurgeService.PurgeResult(
                TENANT_ID, Map.of("sales.quote", 3), startedAt, endsAt));

    GoRealResponse response = service.goReal(context("ADMIN"), request);

    assertThat(response.tenantId()).isEqualTo(TENANT_ID);
    assertThat(response.demoMode()).isFalse();
    assertThat(response.trialStartedAt()).isEqualTo(startedAt);
    assertThat(response.trialEndsAt()).isEqualTo(endsAt);
    assertThat(response.deletedRows()).containsEntry("sales.quote", 3);
    verify(userOnboardingService).completeOnboarding(USER_ID, request);
    verify(purgeService).goReal(TENANT_ID);
  }

  @Test
  void shouldRefuseAlreadyRealTenantWithoutPurging() {
    when(tenantSystemService.findById(TENANT_ID))
        .thenReturn(Optional.of(TenantDto.builder().id(TENANT_ID).demoMode(false).build()));

    assertThatThrownBy(() -> service.goReal(context("ADMIN"), null))
        .isInstanceOf(PlatformDomainException.class)
        .extracting("errorCode", "httpStatus")
        .containsExactly("TENANT_ALREADY_REAL", 409);

    verifyNoInteractions(userRepository, userOnboardingService, purgeService);
  }

  @Test
  void shouldRefuseDemoSeedCallerWithoutPurging() {
    User seeded = owner();
    seeded.setDemoSeed(true);
    when(tenantSystemService.findById(TENANT_ID))
        .thenReturn(Optional.of(TenantDto.builder().id(TENANT_ID).demoMode(true).build()));
    when(userRepository.findByTenantIdAndId(TENANT_ID, USER_ID)).thenReturn(Optional.of(seeded));

    assertThatThrownBy(() -> service.goReal(context("ADMIN"), null))
        .isInstanceOf(PlatformDomainException.class)
        .extracting("errorCode", "httpStatus")
        .containsExactly("GO_REAL_REQUIRES_OWNER", 403);

    verifyNoInteractions(userOnboardingService, purgeService);
  }

  @Test
  void shouldRefuseNonAdminCallerWithoutPurging() {
    User worker = owner();
    worker.setRole(Role.create("Worker", "WORKER", "Worker"));
    when(tenantSystemService.findById(TENANT_ID))
        .thenReturn(Optional.of(TenantDto.builder().id(TENANT_ID).demoMode(true).build()));
    when(userRepository.findByTenantIdAndId(TENANT_ID, USER_ID)).thenReturn(Optional.of(worker));

    assertThatThrownBy(() -> service.goReal(context("WORKER"), null))
        .isInstanceOf(PlatformDomainException.class)
        .extracting("errorCode", "httpStatus")
        .containsExactly("GO_REAL_REQUIRES_OWNER", 403);

    verifyNoInteractions(userOnboardingService, purgeService);
  }

  private AuthenticatedUserContext context(String roleCode) {
    return new AuthenticatedUserContext(USER_ID, roleCode, List.of(), null, TENANT_ID);
  }

  private User owner() {
    User user = User.create("Owner", "User", UUID.randomUUID());
    user.setId(USER_ID);
    user.setTenantId(TENANT_ID);
    user.setRole(Role.create("Admin", "ADMIN", "Admin"));
    return user;
  }
}
