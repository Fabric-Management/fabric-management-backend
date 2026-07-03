package com.fabricmanagement.platform.auth.app.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.platform.auth.app.IdentityProvisioningService;
import com.fabricmanagement.platform.auth.domain.AuthUser;
import com.fabricmanagement.platform.auth.domain.LoginIdentity;
import com.fabricmanagement.platform.auth.domain.MfaType;
import com.fabricmanagement.platform.auth.infra.repository.AuthUserRepository;
import com.fabricmanagement.platform.communication.app.ContactService;
import com.fabricmanagement.platform.communication.domain.Contact;
import com.fabricmanagement.platform.communication.domain.ContactType;
import com.fabricmanagement.platform.user.app.UserContactAssignmentService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class ProvisionExistingIdentityStepTest {

  private final IdentityProvisioningService identityProvisioningService =
      Mockito.mock(IdentityProvisioningService.class);
  private final AuthUserRepository authUserRepository = Mockito.mock(AuthUserRepository.class);
  private final ContactService contactService = Mockito.mock(ContactService.class);
  private final UserContactAssignmentService userContactAssignmentService =
      Mockito.mock(UserContactAssignmentService.class);
  private final ProvisionExistingIdentityStep step =
      new ProvisionExistingIdentityStep(
          identityProvisioningService,
          authUserRepository,
          contactService,
          userContactAssignmentService);

  @Test
  void provisionsMembershipAuthUserMirrorAndVerifiedContactForExistingIdentity() {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID contactId = UUID.randomUUID();
    LoginIdentity identity =
        LoginIdentity.builder()
            .id(UUID.randomUUID())
            .email("owner@example.com")
            .passwordHash("existing-password-hash")
            .isMfaEnabled(true)
            .primaryMfaType(MfaType.TOTP)
            .mfaSecret("secret")
            .isActive(true)
            .emailVerified(true)
            .build();
    Contact contact =
        Contact.builder()
            .contactValue("owner@example.com")
            .contactType(ContactType.EMAIL)
            .isVerified(false)
            .build();
    contact.setId(contactId);
    contact.setTenantId(tenantId);
    OnboardingContext context = new OnboardingContext();
    context.setExistingIdentity(true);
    context.setTenantId(tenantId);
    context.setUserId(userId);
    context.setAdminContact("owner@example.com");
    when(identityProvisioningService.provisionMembershipForExistingIdentity(
            "owner@example.com", tenantId, userId))
        .thenReturn(identity);
    when(authUserRepository.existsByUserId(userId)).thenReturn(false);
    when(contactService.findByValue("owner@example.com")).thenReturn(Optional.of(contact));
    when(contactService.verifyContact(contactId)).thenReturn(contact);
    when(userContactAssignmentService.existsUserContact(userId, contactId)).thenReturn(false);

    step.execute(context);

    verify(identityProvisioningService)
        .provisionMembershipForExistingIdentity("owner@example.com", tenantId, userId);
    ArgumentCaptor<AuthUser> authUserCaptor = ArgumentCaptor.forClass(AuthUser.class);
    verify(authUserRepository).save(authUserCaptor.capture());
    AuthUser authUser = authUserCaptor.getValue();
    assertThat(authUser.getTenantId()).isEqualTo(tenantId);
    assertThat(authUser.getUserId()).isEqualTo(userId);
    assertThat(authUser.getPasswordHash()).isEqualTo("existing-password-hash");
    assertThat(authUser.getIsVerified()).isTrue();
    assertThat(authUser.getIsMfaEnabled()).isTrue();
    assertThat(authUser.getPrimaryMfaType()).isEqualTo(MfaType.TOTP);
    assertThat(authUser.getMfaSecret()).isEqualTo("secret");
    verify(contactService).verifyContact(contactId);
    verify(userContactAssignmentService).assignContact(userId, contactId, true);
  }

  @Test
  void skipsRegularOnboarding() {
    OnboardingContext context = new OnboardingContext();
    context.setExistingIdentity(false);

    step.execute(context);

    verify(identityProvisioningService, never())
        .provisionMembershipForExistingIdentity(Mockito.anyString(), Mockito.any(), Mockito.any());
    verify(authUserRepository, never()).save(Mockito.any(AuthUser.class));
  }
}
