package com.fabricmanagement.platform.auth.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.persistence.TenantSessionBinder;
import com.fabricmanagement.common.infrastructure.tenant.TenantQueryPort;
import com.fabricmanagement.platform.auth.domain.RefreshToken;
import com.fabricmanagement.platform.auth.domain.event.UserLogoutEvent;
import com.fabricmanagement.platform.auth.infra.repository.RefreshTokenRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LogoutServiceTest {

  @Mock private RefreshTokenRepository refreshTokenRepository;
  @Mock private DomainEventPublisher eventPublisher;
  @Mock private TenantQueryPort tenantQueryPort;
  @Mock private TenantSessionBinder tenantSessionBinder;

  @InjectMocks private LogoutService logoutService;

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void shouldResolveTenantThenLogoutByRefreshToken() {
    UUID tenantId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    String refreshTokenValue = "refresh-token";
    RefreshToken refreshToken =
        RefreshToken.create(
            userId,
            refreshTokenValue,
            Instant.now().plusSeconds(60),
            "127.0.0.1",
            "agent",
            "browser");
    refreshToken.setId(UUID.randomUUID());
    refreshToken.setTenantId(tenantId);

    when(tenantQueryPort.findTenantIdByRefreshToken(refreshTokenValue))
        .thenReturn(Optional.of(tenantId));
    when(refreshTokenRepository.findByToken(refreshTokenValue))
        .thenReturn(Optional.of(refreshToken));

    logoutService.logoutByRefreshToken(refreshTokenValue);

    assertThat(refreshToken.getIsRevoked()).isTrue();
    verify(tenantSessionBinder).bindToCurrentSession(tenantId);
    verify(refreshTokenRepository).save(refreshToken);

    ArgumentCaptor<UserLogoutEvent> eventCaptor = ArgumentCaptor.forClass(UserLogoutEvent.class);
    verify(eventPublisher).publish(eventCaptor.capture());
    assertThat(eventCaptor.getValue().getTenantId()).isEqualTo(tenantId);
    assertThat(eventCaptor.getValue().getUserId()).isEqualTo(userId);

    InOrder inOrder = inOrder(tenantQueryPort, tenantSessionBinder, refreshTokenRepository);
    inOrder.verify(tenantQueryPort).findTenantIdByRefreshToken(refreshTokenValue);
    inOrder.verify(tenantSessionBinder).bindToCurrentSession(tenantId);
    inOrder.verify(refreshTokenRepository).findByToken(refreshTokenValue);
  }

  @Test
  void shouldSkipRepositoryLookupWhenRefreshTokenTenantCannotBeResolved() {
    when(tenantQueryPort.findTenantIdByRefreshToken("missing-refresh-token"))
        .thenReturn(Optional.empty());

    logoutService.logoutByRefreshToken("missing-refresh-token");

    verify(refreshTokenRepository, never()).findByToken(any());
    verify(refreshTokenRepository, never()).save(any());
    verify(eventPublisher, never()).publish(any());
  }
}
