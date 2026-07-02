package com.fabricmanagement.platform.auth.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.persistence.TenantSessionBinder;
import com.fabricmanagement.common.infrastructure.tenant.TenantQueryPort;
import com.fabricmanagement.platform.auth.domain.RefreshToken;
import com.fabricmanagement.platform.auth.dto.LoginResponse;
import com.fabricmanagement.platform.auth.infra.repository.RefreshTokenRepository;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.communication.domain.Contact;
import com.fabricmanagement.platform.communication.domain.ContactType;
import com.fabricmanagement.platform.user.api.facade.UserFacade;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.domain.UserContact;
import com.fabricmanagement.platform.user.dto.UserDto;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

  @Mock private RefreshTokenRepository refreshTokenRepository;
  @Mock private UserRepository userRepository;
  @Mock private UserFacade userFacade;
  @Mock private JwtService jwtService;
  @Mock private TenantQueryPort tenantQueryPort;
  @Mock private TenantSessionBinder tenantSessionBinder;

  @InjectMocks private RefreshTokenService refreshTokenService;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(refreshTokenService, "accessTokenExpiration", 900_000L);
    ReflectionTestUtils.setField(refreshTokenService, "refreshTokenExpiration", 604_800_000L);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void shouldResolveTenantThenRotateRefreshToken() {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    String oldTokenValue = "old-refresh-token";
    String newTokenValue = "new-refresh-token";
    RefreshToken oldToken =
        refreshToken(userId, tenantId, oldTokenValue, Instant.now().plusSeconds(60));
    User user = user(tenantId, userId);
    UserDto userDto = UserDto.builder().id(userId).tenantId(tenantId).uid("TEN-001-USER-1").build();

    when(tenantQueryPort.findTenantIdByRefreshToken(oldTokenValue))
        .thenReturn(Optional.of(tenantId));
    when(refreshTokenRepository.findByToken(oldTokenValue)).thenReturn(Optional.of(oldToken));
    when(userRepository.findByTenantIdAndId(tenantId, userId)).thenReturn(Optional.of(user));
    when(jwtService.generateAccessToken(user)).thenReturn("new-access-token");
    when(jwtService.generateRefreshToken(user)).thenReturn(newTokenValue);
    when(userFacade.findById(tenantId, userId)).thenReturn(Optional.of(userDto));

    LoginResponse response = refreshTokenService.refreshAccessToken(oldTokenValue);

    assertThat(response.getAccessToken()).isEqualTo("new-access-token");
    assertThat(response.getRefreshToken()).isEqualTo(newTokenValue);
    assertThat(oldToken.getIsRevoked()).isTrue();
    verify(tenantSessionBinder).bindToCurrentSession(tenantId);

    InOrder inOrder = inOrder(tenantQueryPort, tenantSessionBinder, refreshTokenRepository);
    inOrder.verify(tenantQueryPort).findTenantIdByRefreshToken(oldTokenValue);
    inOrder.verify(tenantSessionBinder).bindToCurrentSession(tenantId);
    inOrder.verify(refreshTokenRepository).findByToken(oldTokenValue);

    ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
    verify(refreshTokenRepository, org.mockito.Mockito.times(2)).save(tokenCaptor.capture());
    assertThat(tokenCaptor.getAllValues().get(1).getToken()).isEqualTo(newTokenValue);
    assertThat(tokenCaptor.getAllValues().get(1).getUserId()).isEqualTo(userId);
  }

  @Test
  void shouldRejectInvalidRefreshTokenBeforeRlsBoundLookup() {
    when(tenantQueryPort.findTenantIdByRefreshToken("missing-token")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> refreshTokenService.refreshAccessToken("missing-token"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid refresh token");

    verify(refreshTokenRepository, never()).findByToken(any());
  }

  @Test
  void shouldRejectExpiredRefreshToken() {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    RefreshToken token =
        refreshToken(userId, tenantId, "expired-token", Instant.now().minusSeconds(1));

    when(tenantQueryPort.findTenantIdByRefreshToken("expired-token"))
        .thenReturn(Optional.of(tenantId));
    when(refreshTokenRepository.findByToken("expired-token")).thenReturn(Optional.of(token));

    assertThatThrownBy(() -> refreshTokenService.refreshAccessToken("expired-token"))
        .isInstanceOf(PlatformDomainException.class)
        .satisfies(
            exception ->
                assertThat(((PlatformDomainException) exception).getErrorCode())
                    .isEqualTo("AUTH_REFRESH_TOKEN_INVALID"));
  }

  @Test
  void shouldRejectRevokedRefreshToken() {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    RefreshToken token =
        refreshToken(userId, tenantId, "revoked-token", Instant.now().plusSeconds(60));
    token.revoke();

    when(tenantQueryPort.findTenantIdByRefreshToken("revoked-token"))
        .thenReturn(Optional.of(tenantId));
    when(refreshTokenRepository.findByToken("revoked-token")).thenReturn(Optional.of(token));

    assertThatThrownBy(() -> refreshTokenService.refreshAccessToken("revoked-token"))
        .isInstanceOf(PlatformDomainException.class)
        .satisfies(
            exception ->
                assertThat(((PlatformDomainException) exception).getErrorCode())
                    .isEqualTo("AUTH_REFRESH_TOKEN_INVALID"));
  }

  private RefreshToken refreshToken(UUID userId, UUID tenantId, String token, Instant expiresAt) {
    RefreshToken refreshToken =
        RefreshToken.create(userId, token, expiresAt, "127.0.0.1", "agent", "browser");
    refreshToken.setId(UUID.randomUUID());
    refreshToken.setTenantId(tenantId);
    return refreshToken;
  }

  private User user(UUID tenantId, UUID userId) {
    Contact contact =
        Contact.builder()
            .contactType(ContactType.EMAIL)
            .contactValue("refresh@example.com")
            .isVerified(true)
            .build();
    User user = User.create("Refresh", "User", UUID.randomUUID());
    user.setId(userId);
    user.setTenantId(tenantId);
    user.setUid("TEN-001-USER-1");
    user.getUserContacts().add(UserContact.builder().contact(contact).isDefault(true).build());
    return user;
  }
}
