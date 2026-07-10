package com.fabricmanagement.platform.auth.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.config.FrontendUrlProvider;
import com.fabricmanagement.common.infrastructure.web.LocalizationService;
import com.fabricmanagement.platform.auth.domain.AuthUser;
import com.fabricmanagement.platform.auth.domain.LoginIdentity;
import com.fabricmanagement.platform.auth.domain.MfaType;
import com.fabricmanagement.platform.auth.domain.RegistrationToken;
import com.fabricmanagement.platform.auth.infra.repository.AuthUserRepository;
import com.fabricmanagement.platform.auth.infra.repository.LoginIdentityRepository;
import com.fabricmanagement.platform.auth.infra.repository.RegistrationTokenRepository;
import com.fabricmanagement.platform.communication.app.ContactService;
import com.fabricmanagement.platform.communication.app.EmailTemplateRenderer;
import com.fabricmanagement.platform.communication.app.NotificationService;
import com.fabricmanagement.platform.communication.domain.Contact;
import com.fabricmanagement.platform.communication.domain.ContactType;
import com.fabricmanagement.platform.organization.app.OrganizationService;
import com.fabricmanagement.platform.organization.dto.OrganizationDto;
import com.fabricmanagement.platform.user.app.UserContactAssignmentService;
import com.fabricmanagement.platform.user.domain.event.UserCreatedEvent;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserInvitationEventListenerTest {

  @Mock private AuthUserRepository authUserRepository;
  @Mock private LoginIdentityRepository loginIdentityRepository;
  @Mock private RegistrationTokenRepository registrationTokenRepository;
  @Mock private IdentityProvisioningService identityProvisioningService;
  @Mock private ContactService contactService;
  @Mock private UserContactAssignmentService userContactAssignmentService;
  @Mock private OrganizationService organizationService;
  @Mock private NotificationService notificationService;
  @Mock private EmailTemplateRenderer emailTemplateRenderer;
  @Mock private FrontendUrlProvider frontendUrlProvider;
  @Mock private LocalizationService localizationService;

  private UserInvitationEventListener listener;

  @BeforeEach
  void setUp() {
    listener =
        new UserInvitationEventListener(
            authUserRepository,
            loginIdentityRepository,
            registrationTokenRepository,
            identityProvisioningService,
            contactService,
            userContactAssignmentService,
            organizationService,
            notificationService,
            emailTemplateRenderer,
            frontendUrlProvider,
            localizationService);
  }

  @Test
  void suppressedUserCreatedEventDoesNotQueueInvitationEmail() {
    UserCreatedEvent event =
        new UserCreatedEvent(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Demo Persona",
            "owner+demo@example.com",
            UUID.randomUUID(),
            true);

    listener.provisionExistingIdentityMembership(event);
    listener.onUserCreated(event);

    verifyNoInteractions(loginIdentityRepository);
    verifyNoInteractions(registrationTokenRepository);
    verifyNoInteractions(notificationService);
  }

  @Test
  void shouldAutoAddExistingIdentityAndSendAddedToOrganizationEmail() {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID organizationId = UUID.randomUUID();
    UserCreatedEvent event =
        new UserCreatedEvent(
            tenantId, userId, "Ada Lovelace", " Existing@Example.COM ", organizationId, false);
    LoginIdentity identity = identity("existing@example.com", "existing-hash");
    Contact contact = contact(UUID.randomUUID(), tenantId, "existing@example.com");
    OrganizationDto organization = OrganizationDto.builder().name("Nexus Fabrics").build();

    when(authUserRepository.existsByUserId(userId)).thenReturn(false);
    when(registrationTokenRepository.existsByContactValueAndIsUsedFalseAndExpiresAtAfter(
            eq(event.getContactValue()), any(Instant.class)))
        .thenReturn(false);
    when(loginIdentityRepository.findByEmail("existing@example.com"))
        .thenReturn(Optional.of(identity));
    when(identityProvisioningService.provisionMembershipForExistingIdentity(
            event.getContactValue(), tenantId, userId))
        .thenReturn(identity);
    // Contact lookup uses the RAW event value (contacts are stored as-typed, not lowercased)
    when(contactService.findByValue(event.getContactValue())).thenReturn(Optional.of(contact));
    when(contactService.verifyContact(contact.getId())).thenReturn(contact);
    when(userContactAssignmentService.existsUserContact(userId, contact.getId())).thenReturn(false);
    when(organizationService.findById(tenantId, organizationId))
        .thenReturn(Optional.of(organization));
    when(localizationService.resolveLocaleForUser(tenantId, userId)).thenReturn(Locale.ENGLISH);
    when(localizationService.getMessage("email.added-to-org.subject", null, Locale.ENGLISH))
        .thenReturn("You were added to an organization in FabricOS");
    when(emailTemplateRenderer.renderAddedToOrganization(
            "Ada", "Nexus Fabrics", event.getContactValue()))
        .thenReturn("added-html");

    listener.provisionExistingIdentityMembership(event);
    listener.onUserCreated(event);

    verify(identityProvisioningService)
        .provisionMembershipForExistingIdentity(event.getContactValue(), tenantId, userId);

    ArgumentCaptor<AuthUser> authUserCaptor = ArgumentCaptor.forClass(AuthUser.class);
    verify(authUserRepository).save(authUserCaptor.capture());
    assertThat(authUserCaptor.getValue().getUserId()).isEqualTo(userId);
    assertThat(authUserCaptor.getValue().getTenantId()).isEqualTo(tenantId);
    assertThat(authUserCaptor.getValue().getPasswordHash()).isEqualTo("existing-hash");
    assertThat(authUserCaptor.getValue().getIsVerified()).isTrue();

    verify(contactService).verifyContact(contact.getId());
    verify(userContactAssignmentService).assignContact(userId, contact.getId(), true);
    verify(registrationTokenRepository, never()).save(any(RegistrationToken.class));
    verify(emailTemplateRenderer, never())
        .renderSetupPassword(anyString(), anyString(), anyString(), anyString());
    verify(notificationService)
        .sendNotificationSync(
            event.getTenantId(),
            event.getContactValue(),
            "You were added to an organization in FabricOS",
            "added-html");
  }

  @Test
  void shouldKeepSetupInvitationFlowForNewEmail() {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID organizationId = UUID.randomUUID();
    UserCreatedEvent event =
        new UserCreatedEvent(
            tenantId, userId, "Grace Hopper", "new@example.com", organizationId, false);

    when(loginIdentityRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
    when(authUserRepository.existsByUserId(userId)).thenReturn(false);
    when(registrationTokenRepository.existsByContactValueAndIsUsedFalseAndExpiresAtAfter(
            eq(event.getContactValue()), any(Instant.class)))
        .thenReturn(false);
    when(registrationTokenRepository.save(any(RegistrationToken.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(frontendUrlProvider.buildUrl(startsWith("/setup?token="))).thenReturn("setup-url");
    when(localizationService.resolveLocaleForUser(tenantId, userId)).thenReturn(Locale.ENGLISH);
    when(localizationService.getMessage("email.invitation.subject", null, Locale.ENGLISH))
        .thenReturn("You've been invited to FabricOS");
    when(emailTemplateRenderer.renderSetupPassword("Grace", "", "new@example.com", "setup-url"))
        .thenReturn("setup-html");

    listener.provisionExistingIdentityMembership(event);
    listener.onUserCreated(event);

    verify(identityProvisioningService, never())
        .provisionMembershipForExistingIdentity(anyString(), any(UUID.class), any(UUID.class));
    verify(authUserRepository, never()).save(any(AuthUser.class));
    verify(registrationTokenRepository).save(any(RegistrationToken.class));
    verify(emailTemplateRenderer).renderSetupPassword("Grace", "", "new@example.com", "setup-url");
    verify(emailTemplateRenderer, never())
        .renderAddedToOrganization(anyString(), anyString(), anyString());
    verify(notificationService)
        .sendNotificationSync(
            event.getTenantId(),
            "new@example.com",
            "You've been invited to FabricOS",
            "setup-html");
  }

  private LoginIdentity identity(String email, String passwordHash) {
    return LoginIdentity.builder()
        .id(UUID.randomUUID())
        .email(email)
        .passwordHash(passwordHash)
        .isMfaEnabled(false)
        .primaryMfaType(MfaType.NONE)
        .isActive(true)
        .emailVerified(true)
        .failedLoginAttempts(0)
        .requiresPasswordReset(false)
        .build();
  }

  private Contact contact(UUID contactId, UUID tenantId, String value) {
    Contact contact =
        Contact.builder()
            .contactValue(value)
            .contactType(ContactType.EMAIL)
            .label("Primary")
            .isPersonal(true)
            .isVerified(false)
            .build();
    contact.setId(contactId);
    contact.setTenantId(tenantId);
    return contact;
  }
}
