package com.fabricmanagement.platform.auth.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.platform.auth.domain.LoginIdentity;
import com.fabricmanagement.platform.auth.domain.Membership;
import com.fabricmanagement.platform.auth.domain.MembershipStatus;
import com.fabricmanagement.platform.auth.domain.MfaType;
import com.fabricmanagement.platform.auth.infra.repository.LoginIdentityRepository;
import com.fabricmanagement.platform.auth.infra.repository.MembershipRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IdentityProvisioningServiceTest {

  @Mock private LoginIdentityRepository loginIdentityRepository;
  @Mock private MembershipRepository membershipRepository;

  @Test
  void shouldProvisionNewIdentityAndDefaultMembership() {
    UUID identityId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    IdentityProvisioningService service =
        new IdentityProvisioningService(loginIdentityRepository, membershipRepository);

    when(loginIdentityRepository.findByEmail("admin@example.com")).thenReturn(Optional.empty());
    when(loginIdentityRepository.save(any(LoginIdentity.class)))
        .thenAnswer(
            invocation -> {
              LoginIdentity identity = invocation.getArgument(0);
              identity.setId(identityId);
              return identity;
            });
    when(membershipRepository.findByUserId(userId)).thenReturn(Optional.empty());
    when(membershipRepository.findByLoginIdentityIdAndTenantId(identityId, tenantId))
        .thenReturn(Optional.empty());

    service.provisionCredential(
        " Admin@Example.COM ", "hash", false, MfaType.NONE, null, true, tenantId, userId);

    ArgumentCaptor<LoginIdentity> identityCaptor = ArgumentCaptor.forClass(LoginIdentity.class);
    verify(loginIdentityRepository).save(identityCaptor.capture());
    assertThat(identityCaptor.getValue().getEmail()).isEqualTo("admin@example.com");
    assertThat(identityCaptor.getValue().getPasswordHash()).isEqualTo("hash");
    assertThat(identityCaptor.getValue().getRequiresPasswordReset()).isFalse();

    ArgumentCaptor<Membership> membershipCaptor = ArgumentCaptor.forClass(Membership.class);
    verify(membershipRepository).save(membershipCaptor.capture());
    assertThat(membershipCaptor.getValue().getLoginIdentityId()).isEqualTo(identityId);
    assertThat(membershipCaptor.getValue().getTenantId()).isEqualTo(tenantId);
    assertThat(membershipCaptor.getValue().getUserId()).isEqualTo(userId);
    assertThat(membershipCaptor.getValue().getStatus()).isEqualTo(MembershipStatus.ACTIVE);
    assertThat(membershipCaptor.getValue().getIsDefault()).isTrue();
  }

  @Test
  void shouldBeIdempotentWhenUserMembershipAlreadyExists() {
    UUID identityId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    LoginIdentity identity = identity(identityId, "admin@example.com", "old-hash");
    Membership membership =
        Membership.builder()
            .loginIdentityId(identityId)
            .tenantId(tenantId)
            .userId(userId)
            .status(MembershipStatus.ACTIVE)
            .isDefault(true)
            .build();
    IdentityProvisioningService service =
        new IdentityProvisioningService(loginIdentityRepository, membershipRepository);

    when(loginIdentityRepository.findByEmail("admin@example.com"))
        .thenReturn(Optional.of(identity));
    when(membershipRepository.countByLoginIdentityId(identityId)).thenReturn(1L);
    when(membershipRepository.findByUserId(userId)).thenReturn(Optional.of(membership));

    service.provisionCredential(
        "admin@example.com", "new-hash", false, MfaType.NONE, null, true, tenantId, userId);

    assertThat(identity.getPasswordHash()).isEqualTo("old-hash");
    assertThat(identity.getRequiresPasswordReset()).isFalse();
    verify(loginIdentityRepository, never()).save(any(LoginIdentity.class));
    verify(membershipRepository, never()).save(any(Membership.class));
  }

  @Test
  void shouldMergeEmailCollisionWithoutOverwritingPassword() {
    UUID identityId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    LoginIdentity identity = identity(identityId, "admin@example.com", "old-hash");
    IdentityProvisioningService service =
        new IdentityProvisioningService(loginIdentityRepository, membershipRepository);

    when(loginIdentityRepository.findByEmail("admin@example.com"))
        .thenReturn(Optional.of(identity));
    when(membershipRepository.countByLoginIdentityId(identityId)).thenReturn(1L);
    when(membershipRepository.findByUserId(userId)).thenReturn(Optional.empty());
    when(loginIdentityRepository.save(identity)).thenReturn(identity);
    when(membershipRepository.findByLoginIdentityIdAndTenantId(identityId, tenantId))
        .thenReturn(Optional.empty());

    service.provisionCredential(
        "admin@example.com", "new-hash", false, MfaType.NONE, null, true, tenantId, userId);

    assertThat(identity.getPasswordHash()).isEqualTo("old-hash");
    assertThat(identity.getRequiresPasswordReset()).isTrue();

    ArgumentCaptor<Membership> membershipCaptor = ArgumentCaptor.forClass(Membership.class);
    verify(membershipRepository).save(membershipCaptor.capture());
    assertThat(membershipCaptor.getValue().getLoginIdentityId()).isEqualTo(identityId);
    assertThat(membershipCaptor.getValue().getTenantId()).isEqualTo(tenantId);
    assertThat(membershipCaptor.getValue().getUserId()).isEqualTo(userId);
    assertThat(membershipCaptor.getValue().getIsDefault()).isFalse();
  }

  private LoginIdentity identity(UUID identityId, String email, String passwordHash) {
    return LoginIdentity.builder()
        .id(identityId)
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
}
