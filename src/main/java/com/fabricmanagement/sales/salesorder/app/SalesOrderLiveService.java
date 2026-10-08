package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.platform.auth.app.LiveAuthenticationQueryService;
import com.fabricmanagement.platform.auth.app.LiveAuthenticationQueryService.VerifiedAccessToken;
import com.fabricmanagement.platform.realtime.app.LiveStreamService;
import com.fabricmanagement.platform.realtime.domain.LiveActor;
import com.fabricmanagement.platform.realtime.domain.exception.LiveStreamRejectedException;
import com.fabricmanagement.platform.realtime.domain.exception.LiveStreamRejectedException.Rejection;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * Opens the live change stream of one sales order (CEDIT-05 §3–4). The actor comes only from the
 * authenticated request: the principal the JWT filter built and the same request's verified token,
 * which gives the expiry the stream may not outlive. Partner accounts are refused here: their
 * restriction lives on the request and would be lost on the background checks.
 */
@Service
@RequiredArgsConstructor
public class SalesOrderLiveService {

  private static final String PARTNER_AUTHORITY = "PARTNER_USER";

  private final LiveStreamService liveStreams;
  private final SalesOrderLiveRevisionSource revisions;
  private final LiveAuthenticationQueryService authentications;

  /**
   * Opens the stream on this request: refusals are thrown before the response is committed; on
   * success the response is the asynchronous event stream.
   */
  public void subscribe(
      UUID orderId,
      Authentication authentication,
      String accessToken,
      HttpServletRequest request,
      HttpServletResponse response) {
    liveStreams.subscribe(
        actor(authentication, accessToken), revisions, orderId, request, response);
  }

  LiveActor actor(Authentication authentication, String accessToken) {
    if (authentication == null
        || !(authentication.getPrincipal() instanceof AuthenticatedUserContext principal)
        || principal.userId() == null
        || principal.tenantId() == null) {
      throw new LiveStreamRejectedException(Rejection.UNAUTHENTICATED);
    }
    VerifiedAccessToken token =
        authentications
            .verify(accessToken)
            .orElseThrow(() -> new LiveStreamRejectedException(Rejection.UNAUTHENTICATED));
    if (!principal.userId().equals(token.userId())
        || !principal.tenantId().equals(token.tenantId())) {
      throw new LiveStreamRejectedException(Rejection.UNAUTHENTICATED);
    }
    boolean partnerAuthority =
        authentication.getAuthorities().stream()
            .anyMatch(authority -> PARTNER_AUTHORITY.equals(authority.getAuthority()));
    if (token.partner() || partnerAuthority) {
      throw new LiveStreamRejectedException(
          Rejection.FORBIDDEN, "Partner accounts cannot subscribe to sales order live events");
    }
    return new LiveActor(token.tenantId(), token.userId(), token.expiresAt());
  }
}
