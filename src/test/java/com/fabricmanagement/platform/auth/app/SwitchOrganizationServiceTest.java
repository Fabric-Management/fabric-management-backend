package com.fabricmanagement.platform.auth.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.persistence.TenantSessionBinder;
import com.fabricmanagement.common.infrastructure.tenant.TenantQueryPort;
import com.fabricmanagement.common.infrastructure.tenant.TenantReference;
import com.fabricmanagement.platform.auth.domain.Membership;
import com.fabricmanagement.platform.auth.domain.MembershipStatus;
import com.fabricmanagement.platform.auth.domain.RefreshToken;
import com.fabricmanagement.platform.auth.dto.LoginResponse;
import com.fabricmanagement.platform.auth.dto.OrganizationMembershipDto;
import com.fabricmanagement.platform.auth.infra.repository.MembershipRepository;
import com.fabricmanagement.platform.auth.infra.repository.RefreshTokenRepository;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.communication.domain.Contact;
import com.fabricmanagement.platform.communication.domain.ContactType;
import com.fabricmanagement.platform.user.api.facade.UserFacade;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.domain.UserContact;
import com.fabricmanagement.platform.user.dto.UserDto;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SwitchOrganizationServiceTest {

  @Mock private MembershipRepository membershipRepository;
  @Mock private RefreshTokenRepository refreshTokenRepository;
  @Mock private UserRepository userRepository;
  @Mock private UserFacade userFacade;
  @Mock private TenantQueryPort tenantQueryPort;
  @Mock private TenantSessionBinder tenantSessionBinder;
  @Mock private JwtService jwtService;

  @InjectMocks private SwitchOrganizationService switchOrganizationService;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(switchOrganizationService, "accessTokenExpiration", 900_000L);
    ReflectionTestUtils.setField(switchOrganizationService, "refreshTokenExpiration", 604_800_000L);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void shouldListActiveMembershipsForCurrentIdentityWithCurrentFirstOrdering() {
    UUID identityId = UUID.randomUUID();
    UUID currentTenantId = UUID.randomUUID();
    UUID currentUserId = UUID.randomUUID();
    UUID otherTenantId = UUID.randomUUID();
    UUID otherUserId = UUID.randomUUID();
    Membership currentMembership = membership(identityId, currentTenantId, currentUserId, true);
    Membership otherMembership = membership(identityId, otherTenantId, otherUserId, false);

    when(membershipRepository.findByUserId(currentUserId))
        .thenReturn(Optional.of(currentMembership));
    when(membershipRepository.findByLoginIdentityIdAndStatus(identityId, MembershipStatus.ACTIVE))
        .thenReturn(List.of(otherMembership, currentMembership));
    when(tenantQueryPort.findAllByIds(List.of(otherTenantId, currentTenantId)))
        .thenReturn(
            List.of(
                new TenantReference(otherTenantId, "BETA-001", "Beta Textiles", "TENANT"),
                new TenantReference(currentTenantId, "ACME-001", "Acme Fabrics", "TENANT")));

    List<OrganizationMembershipDto> memberships =
        switchOrganizationService.getMemberships(currentUserId, currentTenantId);

    assertThat(memberships).hasSize(2);
    assertThat(memberships.get(0).tenantId()).isEqualTo(currentTenantId);
    assertThat(memberships.get(0).tenantName()).isEqualTo("Acme Fabrics");
    assertThat(memberships.get(0).isCurrent()).isTrue();
    assertThat(memberships.get(0).isDefault()).isTrue();
    assertThat(memberships.get(1).tenantId()).isEqualTo(otherTenantId);
    assertThat(memberships.get(1).tenantName()).isEqualTo("Beta Textiles");
    assertThat(memberships.get(1).isCurrent()).isFalse();
  }

  @Test
  void shouldReturnSingleCurrentMembershipFallbackWhenMembershipRowIsMissing() {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    when(membershipRepository.findByUserId(userId)).thenReturn(Optional.empty());
    when(tenantQueryPort.findById(tenantId))
        .thenReturn(
            Optional.of(new TenantReference(tenantId, "ACME-001", "Acme Fabrics", "TENANT")));

    List<OrganizationMembershipDto> memberships =
        switchOrganizationService.getMemberships(userId, tenantId);

    assertThat(memberships)
        .containsExactly(
            new OrganizationMembershipDto(tenantId, "Acme Fabrics", userId, true, true));
  }

  @Test
  void shouldSwitchOrganizationWhenTargetMembershipBelongsToIdentity() {
    UUID identityId = UUID.randomUUID();
    UUID currentTenantId = UUID.randomUUID();
    UUID currentUserId = UUID.randomUUID();
    UUID targetTenantId = UUID.randomUUID();
    UUID targetUserId = UUID.randomUUID();
    Membership currentMembership = membership(identityId, currentTenantId, currentUserId, true);
    Membership targetMembership = membership(identityId, targetTenantId, targetUserId, false);
    User targetUser = user(targetTenantId, targetUserId);
    UserDto targetUserDto = userDto(targetTenantId, targetUserId);

    when(membershipRepository.findByUserId(currentUserId))
        .thenReturn(Optional.of(currentMembership));
    when(membershipRepository.findByLoginIdentityIdAndTenantId(identityId, targetTenantId))
        .thenReturn(Optional.of(targetMembership));
    when(userRepository.findByTenantIdAndId(targetTenantId, targetUserId))
        .thenReturn(Optional.of(targetUser));
    when(jwtService.generateAccessToken(targetUser)).thenReturn("access-token");
    when(jwtService.generateRefreshToken(targetUser)).thenReturn("refresh-token");
    when(userFacade.findById(targetTenantId, targetUserId)).thenReturn(Optional.of(targetUserDto));

    LoginResponse response =
        switchOrganizationService.switchOrganization(
            currentUserId, targetTenantId, "127.0.0.1", "agent");

    assertThat(response.getAccessToken()).isEqualTo("access-token");
    assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
    assertThat(response.getUser()).isSameAs(targetUserDto);
    verify(tenantSessionBinder).bindToCurrentSession(targetTenantId);

    ArgumentCaptor<RefreshToken> refreshTokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
    verify(refreshTokenRepository).save(refreshTokenCaptor.capture());
    assertThat(refreshTokenCaptor.getValue().getUserId()).isEqualTo(targetUserId);
    assertThat(refreshTokenCaptor.getValue().getToken()).isEqualTo("refresh-token");
  }

  @Test
  void shouldRejectSwitchToTenantOutsideCurrentIdentityMemberships() {
    UUID identityId = UUID.randomUUID();
    UUID currentUserId = UUID.randomUUID();
    UUID currentTenantId = UUID.randomUUID();
    UUID targetTenantId = UUID.randomUUID();
    Membership currentMembership = membership(identityId, currentTenantId, currentUserId, true);

    when(membershipRepository.findByUserId(currentUserId))
        .thenReturn(Optional.of(currentMembership));
    when(membershipRepository.findByLoginIdentityIdAndTenantId(identityId, targetTenantId))
        .thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                switchOrganizationService.switchOrganization(
                    currentUserId, targetTenantId, "127.0.0.1", "agent"))
        .isInstanceOf(PlatformDomainException.class)
        .satisfies(
            exception ->
                assertThat(((PlatformDomainException) exception).getErrorCode())
                    .isEqualTo("AUTH_MEMBERSHIP_NOT_FOUND"));

    verify(tenantSessionBinder, never()).bindToCurrentSession(any());
    verify(userRepository, never()).findByTenantIdAndId(any(), any());
    verify(refreshTokenRepository, never()).save(any());
  }

  private Membership membership(UUID identityId, UUID tenantId, UUID userId, boolean isDefault) {
    return Membership.builder()
        .id(UUID.randomUUID())
        .loginIdentityId(identityId)
        .tenantId(tenantId)
        .userId(userId)
        .status(MembershipStatus.ACTIVE)
        .isDefault(isDefault)
        .build();
  }

  private User user(UUID tenantId, UUID userId) {
    Contact contact =
        Contact.builder()
            .contactType(ContactType.EMAIL)
            .contactValue("switch@example.com")
            .isVerified(true)
            .build();
    User user = User.create("Switch", "User", UUID.randomUUID());
    user.setId(userId);
    user.setTenantId(tenantId);
    user.setUid("ACME-001-USER-00001");
    user.getUserContacts().add(UserContact.builder().contact(contact).isDefault(true).build());
    user.completeOnboarding();
    return user;
  }

  private UserDto userDto(UUID tenantId, UUID userId) {
    return UserDto.builder()
        .id(userId)
        .tenantId(tenantId)
        .uid("ACME-001-USER-00001")
        .organizationId(UUID.randomUUID())
        .isActive(true)
        .hasCompletedOnboarding(true)
        .build();
  }
}
