package com.fabricmanagement.platform.tenant.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.bootstrap.DemoTransactionSeeder;
import com.fabricmanagement.common.infrastructure.bootstrap.UserSeeder;
import com.fabricmanagement.common.infrastructure.bootstrap.UserSeeder.PersonaSubset;
import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.common.infrastructure.tenant.TenantAccessPort;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.communication.domain.Contact;
import com.fabricmanagement.platform.communication.domain.ContactType;
import com.fabricmanagement.platform.tenant.dto.ResetDemoResponse;
import com.fabricmanagement.platform.user.app.UserContactAssignmentService;
import com.fabricmanagement.platform.user.domain.Role;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.domain.UserContact;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;

@ExtendWith(MockitoExtension.class)
class TenantResetServiceTest {

  private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID CONTACT_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

  @Mock private TenantAccessPort tenantAccessPort;
  @Mock private UserRepository userRepository;
  @Mock private UserContactAssignmentService userContactAssignmentService;
  @Mock private TenantTransactionalPurgeService purgeService;
  @Mock private UserSeeder userSeeder;
  @Mock private DemoTransactionSeeder demoTransactionSeeder;
  @Mock private CacheManager cacheManager;

  private TenantResetService service;

  @BeforeEach
  void setUp() {
    service =
        new TenantResetService(
            tenantAccessPort,
            userRepository,
            userContactAssignmentService,
            purgeService,
            userSeeder,
            demoTransactionSeeder,
            cacheManager);
  }

  @Test
  void shouldPurgeAndReseedForDemoOwnerWithoutFlippingDemoMode() {
    when(tenantAccessPort.isDemoMode(TENANT_ID)).thenReturn(true);
    when(userRepository.findByTenantIdAndId(TENANT_ID, USER_ID)).thenReturn(Optional.of(owner()));
    when(userContactAssignmentService.getUserContacts(USER_ID))
        .thenReturn(List.of(ownerEmail("owner@example.com")));
    when(purgeService.purgeDemoData(TENANT_ID))
        .thenReturn(
            new TenantTransactionalPurgeService.PurgeDemoDataResult(
                TENANT_ID,
                Map.of("sales.quote", 2, "common_user.common_user(seed-demo-users)", 15)));
    when(userSeeder.seedFor(TENANT_ID, "owner@example.com", PersonaSubset.REPRESENTATIVE))
        .thenReturn(15);

    ResetDemoResponse response = service.reset(context("ADMIN"));

    assertThat(response.tenantId()).isEqualTo(TENANT_ID);
    assertThat(response.demoMode()).isTrue();
    assertThat(response.seededPersonaUsers()).isEqualTo(15);
    assertThat(response.purgedRows()).containsEntry("sales.quote", 2);

    InOrder ordered = inOrder(purgeService, userSeeder, demoTransactionSeeder);
    ordered.verify(purgeService).purgeDemoData(TENANT_ID);
    ordered
        .verify(userSeeder)
        .seedFor(TENANT_ID, "owner@example.com", PersonaSubset.REPRESENTATIVE);
    ordered.verify(demoTransactionSeeder).seedFor(TENANT_ID);
  }

  @Test
  void shouldRefuseNonDemoTenantWithoutPurgingOrSeeding() {
    when(tenantAccessPort.isDemoMode(TENANT_ID)).thenReturn(false);

    assertThatThrownBy(() -> service.reset(context("ADMIN")))
        .isInstanceOf(PlatformDomainException.class)
        .extracting("errorCode", "httpStatus")
        .containsExactly("DEMO_MODE_REQUIRED", 403);

    verifyNoInteractions(
        userRepository,
        userContactAssignmentService,
        purgeService,
        userSeeder,
        demoTransactionSeeder);
  }

  @Test
  void shouldFailClosedWhenDemoModeCannotBeResolved() {
    when(tenantAccessPort.isDemoMode(TENANT_ID))
        .thenThrow(new IllegalStateException("cache unavailable"));

    assertThatThrownBy(() -> service.reset(context("ADMIN")))
        .isInstanceOf(PlatformDomainException.class)
        .extracting("errorCode", "httpStatus")
        .containsExactly("DEMO_MODE_REQUIRED", 403);

    verifyNoInteractions(
        userRepository,
        userContactAssignmentService,
        purgeService,
        userSeeder,
        demoTransactionSeeder);
  }

  @Test
  void shouldRefuseDemoSeedCallerWithoutPurgingOrSeeding() {
    User seeded = owner();
    seeded.setDemoSeed(true);
    when(tenantAccessPort.isDemoMode(TENANT_ID)).thenReturn(true);
    when(userRepository.findByTenantIdAndId(TENANT_ID, USER_ID)).thenReturn(Optional.of(seeded));

    assertThatThrownBy(() -> service.reset(context("ADMIN")))
        .isInstanceOf(PlatformDomainException.class)
        .extracting("errorCode", "httpStatus")
        .containsExactly("RESET_REQUIRES_OWNER", 403);

    verifyNoInteractions(
        userContactAssignmentService, purgeService, userSeeder, demoTransactionSeeder);
  }

  @Test
  void shouldRefuseNonAdminCallerWithoutPurgingOrSeeding() {
    User worker = owner();
    worker.setRole(Role.create("Worker", "WORKER", "Worker"));
    when(tenantAccessPort.isDemoMode(TENANT_ID)).thenReturn(true);
    when(userRepository.findByTenantIdAndId(TENANT_ID, USER_ID)).thenReturn(Optional.of(worker));

    assertThatThrownBy(() -> service.reset(context("WORKER")))
        .isInstanceOf(PlatformDomainException.class)
        .extracting("errorCode", "httpStatus")
        .containsExactly("RESET_REQUIRES_OWNER", 403);

    verifyNoInteractions(
        userContactAssignmentService, purgeService, userSeeder, demoTransactionSeeder);
  }

  private AuthenticatedUserContext context(String roleCode) {
    return new AuthenticatedUserContext(USER_ID, roleCode, List.of(), null, TENANT_ID);
  }

  private User owner() {
    User user = User.create("Owner", "User", UUID.randomUUID());
    user.setId(USER_ID);
    user.setTenantId(TENANT_ID);
    user.setRole(Role.create("Admin", "ADMIN", "Admin"));
    user.setDemoSeed(false);
    return user;
  }

  private UserContact ownerEmail(String email) {
    Contact contact =
        Contact.builder()
            .contactValue(email)
            .contactType(ContactType.EMAIL)
            .isVerified(true)
            .build();
    contact.setId(CONTACT_ID);
    contact.setTenantId(TENANT_ID);
    UserContact userContact =
        UserContact.builder()
            .userId(USER_ID)
            .contactId(CONTACT_ID)
            .contact(contact)
            .isDefault(true)
            .build();
    userContact.setTenantId(TENANT_ID);
    return userContact;
  }
}
