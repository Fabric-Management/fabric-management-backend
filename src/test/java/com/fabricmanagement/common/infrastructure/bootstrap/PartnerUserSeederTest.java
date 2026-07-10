package com.fabricmanagement.common.infrastructure.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.fabricmanagement.platform.auth.app.IdentityProvisioningService;
import com.fabricmanagement.platform.auth.domain.AuthUser;
import com.fabricmanagement.platform.auth.infra.repository.AuthUserRepository;
import com.fabricmanagement.platform.communication.domain.Contact;
import com.fabricmanagement.platform.communication.infra.repository.ContactRepository;
import com.fabricmanagement.platform.organization.domain.Organization;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationRepository;
import com.fabricmanagement.platform.tenant.app.TenantSystemService;
import com.fabricmanagement.platform.tenant.dto.TenantDto;
import com.fabricmanagement.platform.user.app.RoleService;
import com.fabricmanagement.platform.user.app.UserCreationService;
import com.fabricmanagement.platform.user.domain.Role;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.dto.CreateExternalUserRequest;
import com.fabricmanagement.platform.user.dto.UserDto;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class PartnerUserSeederTest {

  @Mock private TenantSystemService tenantService;
  @Mock private OrganizationRepository organizationRepository;
  @Mock private UserCreationService userCreationService;
  @Mock private UserRepository userRepository;
  @Mock private RoleService roleService;
  @Mock private AuthUserRepository authUserRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private TransactionTemplate transactionTemplate;
  @Mock private ContactRepository contactRepository;
  @Mock private IdentityProvisioningService identityProvisioningService;

  @InjectMocks private PartnerUserSeeder partnerUserSeeder;

  @Captor private ArgumentCaptor<CreateExternalUserRequest> requestCaptor;

  private final UUID tenantId = UUID.randomUUID();
  private final TenantDto tenantDto = mock(TenantDto.class);

  @BeforeEach
  void setUp() {
    lenient().when(tenantDto.getId()).thenReturn(tenantId);
    lenient()
        .doAnswer(
            invocation -> {
              java.util.function.Consumer<org.springframework.transaction.TransactionStatus>
                  callback = invocation.getArgument(0);
              callback.accept(null);
              return null;
            })
        .when(transactionTemplate)
        .executeWithoutResult(any());
  }

  @Test
  @DisplayName("Should return order 45 (after TradingPartnerSeeder)")
  void shouldReturnOrder45() {
    assertThat(partnerUserSeeder.getOrder()).isEqualTo(45);
  }

  @Test
  @DisplayName("Should return false when tenant is not found")
  void isSeeded_ShouldReturnFalse_WhenTenantNotFound() {
    when(tenantService.findBySlug(any())).thenReturn(Optional.empty());
    assertThat(partnerUserSeeder.isSeeded()).isFalse();
  }

  @Test
  @DisplayName("Should return true when sentinel email exists")
  void isSeeded_ShouldCheckLastSeededEmail() {
    when(tenantService.findBySlug(any())).thenReturn(Optional.of(tenantDto));
    when(userRepository.existsByTenantIdAndContactValue(
            eq(tenantId), eq("finance@centraltextile.com")))
        .thenReturn(true);

    assertThat(partnerUserSeeder.isSeeded()).isTrue();
  }

  @Test
  @DisplayName("Should create external users successfully when partner orgs exist")
  void seed_ShouldCreateExternalUsers_WhenPartnerOrgsExist() {
    // Arrange
    when(tenantService.findBySlug(any())).thenReturn(Optional.of(tenantDto));

    UUID partnerOrgId = UUID.randomUUID();
    Organization partnerOrg = mock(Organization.class);
    when(partnerOrg.getName()).thenReturn("Oz Cotton Yarns Inc.");
    when(partnerOrg.getId()).thenReturn(partnerOrgId);

    when(organizationRepository.findByTenantIdAndIsActiveTrue(tenantId))
        .thenReturn(List.of(partnerOrg));

    Role ownerRole = mock(Role.class);
    when(roleService.findByCode("PARTNER_OWNER")).thenReturn(Optional.of(ownerRole));

    // First user is "Oscar Ozkan" — let's mock existsByTenantIdAndContactValue to false for Oscar,
    // true for others to test just one
    when(userRepository.existsByTenantIdAndContactValue(eq(tenantId), eq("oscar@ozcotton.com")))
        .thenReturn(false);
    when(userRepository.existsByTenantIdAndContactValue(
            eq(tenantId), argThat(email -> !email.equals("oscar@ozcotton.com"))))
        .thenReturn(true);

    UserDto mockUserDto = mock(UserDto.class);
    UUID createdUserId = UUID.randomUUID();
    when(mockUserDto.getId()).thenReturn(createdUserId);
    when(userCreationService.createExternalUser(any())).thenReturn(mockUserDto);

    when(authUserRepository.existsByUserId(createdUserId)).thenReturn(false);
    when(passwordEncoder.encode(any())).thenReturn("encodedPass");

    User mockEntity = mock(User.class);
    when(userRepository.findById(createdUserId)).thenReturn(Optional.of(mockEntity));

    Contact mockContact = mock(Contact.class);
    when(contactRepository.findByTenantIdAndContactValue(tenantId, "oscar@ozcotton.com"))
        .thenReturn(Optional.of(mockContact));

    // Act
    partnerUserSeeder.seed();

    // Assert
    verify(userCreationService).createExternalUser(requestCaptor.capture());
    CreateExternalUserRequest capturedReq = requestCaptor.getValue();
    assertThat(capturedReq.getContactValue()).isEqualTo("oscar@ozcotton.com");
    assertThat(capturedReq.getOrganizationId()).isEqualTo(partnerOrgId);
    assertThat(capturedReq.isInvitationEmailSuppressed()).isTrue();

    verify(authUserRepository).save(any(AuthUser.class));
    verify(mockContact).verify();
    verify(contactRepository).save(mockContact);
  }

  @Test
  @DisplayName("Should skip user creation when partner org is missing")
  void seed_ShouldSkip_WhenPartnerOrgNotFound() {
    // Arrange
    when(tenantService.findBySlug(any())).thenReturn(Optional.of(tenantDto));
    when(organizationRepository.findByTenantIdAndIsActiveTrue(tenantId)).thenReturn(List.of());

    // Act
    partnerUserSeeder.seed();

    // Assert
    verify(userCreationService, never()).createExternalUser(any());
  }
}
