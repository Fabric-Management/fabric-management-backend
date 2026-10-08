package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.platform.auth.app.LiveAuthenticationQueryService;
import com.fabricmanagement.platform.auth.app.LiveAuthenticationQueryService.VerifiedAccessToken;
import com.fabricmanagement.platform.realtime.app.LiveStreamService;
import com.fabricmanagement.platform.realtime.domain.LiveActor;
import com.fabricmanagement.platform.realtime.domain.exception.LiveStreamRejectedException;
import com.fabricmanagement.platform.realtime.domain.exception.LiveStreamRejectedException.Rejection;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** The actor of a stream comes only from the verified request (CEDIT-05 §4.1–4.2, L02). */
@ExtendWith(MockitoExtension.class)
class SalesOrderLiveServiceTest {

  private final UUID tenant = UUID.randomUUID();
  private final UUID user = UUID.randomUUID();
  private final Instant expiry = Instant.parse("2026-10-07T09:15:00Z");

  @Mock private LiveStreamService liveStreams;
  @Mock private SalesOrderLiveRevisionSource revisions;
  @Mock private LiveAuthenticationQueryService authentications;

  private SalesOrderLiveService service;

  @BeforeEach
  void setUp() {
    service = new SalesOrderLiveService(liveStreams, revisions, authentications);
  }

  @Test
  @DisplayName("L02: the actor is the principal confirmed by the same request's token and expiry")
  void actorFromPrincipalAndToken() {
    when(authentications.verify("token"))
        .thenReturn(Optional.of(new VerifiedAccessToken(user, tenant, expiry, false)));

    LiveActor actor = service.actor(authentication(user, tenant), "token");

    assertThat(actor).isEqualTo(new LiveActor(tenant, user, expiry));
  }

  @Test
  @DisplayName("L02: no principal, no tenant, no valid token or a different token is 401")
  void unverifiedRequestsAreUnauthenticated() {
    assertRejected(null, "token", Rejection.UNAUTHENTICATED);
    assertRejected(authentication(user, null), "token", Rejection.UNAUTHENTICATED);

    when(authentications.verify("bad")).thenReturn(Optional.empty());
    assertRejected(authentication(user, tenant), "bad", Rejection.UNAUTHENTICATED);

    when(authentications.verify("other"))
        .thenReturn(Optional.of(new VerifiedAccessToken(UUID.randomUUID(), tenant, expiry, false)));
    assertRejected(authentication(user, tenant), "other", Rejection.UNAUTHENTICATED);

    when(authentications.verify("other-tenant"))
        .thenReturn(Optional.of(new VerifiedAccessToken(user, UUID.randomUUID(), expiry, false)));
    assertRejected(authentication(user, tenant), "other-tenant", Rejection.UNAUTHENTICATED);
    verifyNoInteractions(liveStreams);
  }

  @Test
  @DisplayName("L02: partner accounts are refused with 403")
  void partnersAreForbidden() {
    when(authentications.verify("partner"))
        .thenReturn(Optional.of(new VerifiedAccessToken(user, tenant, expiry, true)));
    assertRejected(authentication(user, tenant), "partner", Rejection.FORBIDDEN);

    when(authentications.verify("token"))
        .thenReturn(Optional.of(new VerifiedAccessToken(user, tenant, expiry, false)));
    UsernamePasswordAuthenticationToken partnerAuthority =
        new UsernamePasswordAuthenticationToken(
            new AuthenticatedUserContext(user, "WORKER", List.of(), null, tenant),
            null,
            List.of(new SimpleGrantedAuthority("PARTNER_USER")));
    assertRejected(partnerAuthority, "token", Rejection.FORBIDDEN);
  }

  private void assertRejected(Authentication authentication, String token, Rejection expected) {
    assertThatThrownBy(() -> service.actor(authentication, token))
        .isInstanceOfSatisfying(
            LiveStreamRejectedException.class,
            rejected -> assertThat(rejected.rejection()).isEqualTo(expected));
  }

  private static Authentication authentication(UUID user, UUID tenant) {
    return new UsernamePasswordAuthenticationToken(
        new AuthenticatedUserContext(user, "WORKER", List.of("SALES"), "SALES", tenant),
        null,
        List.of());
  }
}
