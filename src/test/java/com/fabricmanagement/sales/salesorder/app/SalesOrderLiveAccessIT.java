package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.platform.realtime.dto.LiveCloseReason;
import com.fabricmanagement.platform.user.domain.DataScope;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CEDIT-05 L02, L03, L10: who may open a stream, and how a running stream ends when access goes.
 * The cached HTTP guard and the stream's fresh checks are told apart with a pinned stale cache.
 */
class SalesOrderLiveAccessIT extends SalesOrderLiveItSupport {

  // ── L02: identity ────────────────────────────────────────────────────────

  @Test
  @DisplayName("L02: no token, a bad signature, an expired or pre-auth token, or no tenant: 401")
  void unauthenticatedRequestsAreRefused() {
    assertRefused(Map.of(), 401);
    assertRefused(bearer(withBrokenSignature(token(actorB))), 401);
    assertRefused(
        bearer(token(actorB, Instant.now().minus(Duration.ofMinutes(1)), claims -> {})), 401);
    assertRefused(
        bearer(
            token(
                actorB,
                Instant.now().plus(Duration.ofMinutes(5)),
                claims -> claims.put("mfa_pre_auth", true))),
        401);
    assertRefused(
        bearer(
            token(
                actorB,
                Instant.now().plus(Duration.ofMinutes(10)),
                claims -> claims.remove("tenant_id"))),
        401);
  }

  @Test
  @DisplayName("L02: a token in the URL authenticates nothing")
  void tokenInTheUrlIsIgnored() {
    String token = token(actorB);
    for (String parameter : new String[] {"access_token", "token", "jwt"}) {
      LiveSse stream =
          LiveSse.open(
              java.net.URI.create(liveUri(port, orderId) + "?" + parameter + "=" + token),
              Map.of());
      try {
        assertThat(stream.status()).isEqualTo(401);
      } finally {
        stream.close();
      }
    }
    assertThat(liveRegistry.reservedCount()).isZero();
  }

  @Test
  @DisplayName("L02: a partner account is refused with 403, whatever its role allows")
  void partnerIsForbidden() {
    String partner =
        token(
            actorB,
            Instant.now().plus(Duration.ofMinutes(10)),
            claims -> {
              claims.put("user_type", "PARTNER");
              claims.put("partner_id", UUID.randomUUID().toString());
            });

    LiveSse stream = assertRefused(bearer(partner), 403);
    assertThat(stream.errorBody()).contains("LIVE_STREAM_FORBIDDEN");
  }

  // ── L03: objects ─────────────────────────────────────────────────────────

  @Test
  @DisplayName("L03: another tenant's user gets 404 for this order and nothing about it")
  void otherTenantIsNotFound() {
    OtherTenant other = otherTenantWithOrder();

    LiveSse stream = assertRefused(bearer(token(other.actor())), 404);
    // The answer echoes only the requested path; nothing about the order itself.
    assertThat(stream.errorBody())
        .contains("LIVE_STREAM_NOT_FOUND")
        .doesNotContain("SO-E-")
        .doesNotContain("revision");
    // The same user reaches its own tenant's order.
    ready(subscribe(port, other.orderId(), bearer(token(other.actor()))));
  }

  @Test
  @DisplayName("L03: OWN and DEPARTMENT scopes outside the order, deleted or unknown orders: 404")
  void outOfScopeOrdersAreNotFound() {
    scopes.put(actorB.id(), DataScope.OWN);
    assertRefused(bearer(token(actorB)), 404);

    // DEPARTMENT: A (the creator) leaves the sales department.
    scopes.put(actorB.id(), DataScope.DEPARTMENT);
    ready(subscribe(actorB));
    jdbc.update(
        "UPDATE common_user.common_user_department SET is_active = false WHERE user_id = ?",
        actorA.id());
    assertRefused(bearer(token(actorB)), 404);

    scopes.put(actorB.id(), DataScope.GLOBAL);
    assertRefused(port, UUID.randomUUID(), bearer(token(actorB)), 404);
    jdbc.update("UPDATE sales_ord.sales_order SET is_active = false WHERE id = ?", orderId);
    assertRefused(bearer(token(actorB)), 404);
  }

  @Test
  @DisplayName("L03: a read-only user may subscribe, also when the order is no longer a draft")
  void readOnlyUserMaySubscribe() {
    readOnlyUsers.add(actorB.id());
    ready(subscribe(actorB));

    sendToPlanning();
    ready(subscribe(actorB));
  }

  @Test
  @DisplayName(
      "L03/L10: the cache still allows, the fresh check does not: refused before the stream")
  void freshCheckDecidesTheOpening() {
    cachedScopes.put(actorB.id(), Optional.of(DataScope.GLOBAL));
    scopes.remove(actorB.id());
    LiveSse noPermission = assertRefused(bearer(token(actorB)), 403);
    assertThat(noPermission.errorBody()).contains("LIVE_STREAM_FORBIDDEN");

    scopes.put(actorB.id(), DataScope.OWN);
    assertRefused(bearer(token(actorB)), 404);
  }

  // ── L10: a running stream loses access ───────────────────────────────────

  @Test
  @DisplayName("L10: narrowed fresh scope (cache unchanged) closes ACCESS_REVOKED; no data after")
  void narrowedScopeRevokes() {
    LiveSse stream = subscribe(actorB);
    ready(stream);

    cachedScopes.put(actorB.id(), Optional.of(DataScope.GLOBAL));
    scopes.put(actorB.id(), DataScope.OWN);

    expectClosed(stream, LiveCloseReason.ACCESS_REVOKED);
    saved(actorA, body(UUID.randomUUID(), open(actorA).baseId(), "notes", set("After revoke")));
    assertThat(stream.received()).noneMatch(frame -> "invalidated".equals(frame.event()));
    awaitCondition(() -> liveRegistry.reservedCount() == 0);
  }

  @Test
  @DisplayName("L10: a removed sales permission closes ACCESS_REVOKED")
  void removedPermissionRevokes() {
    LiveSse stream = subscribe(actorB);
    ready(stream);

    scopes.remove(actorB.id());

    expectClosed(stream, LiveCloseReason.ACCESS_REVOKED);
  }

  @Test
  @DisplayName("L10: the creator leaving the department closes a DEPARTMENT reader")
  void departmentChangeRevokes() {
    scopes.put(actorB.id(), DataScope.DEPARTMENT);
    LiveSse stream = subscribe(actorB);
    ready(stream);

    jdbc.update(
        "UPDATE common_user.common_user_department SET is_active = false WHERE user_id = ?",
        actorA.id());

    expectClosed(stream, LiveCloseReason.ACCESS_REVOKED);
  }

  @Test
  @DisplayName("L10: ownership moving away closes an OWN reader")
  void ownershipChangeRevokes() {
    jdbc.update(
        "UPDATE sales_ord.sales_order SET created_by = ? WHERE id = ?", actorB.id(), orderId);
    scopes.put(actorB.id(), DataScope.OWN);
    LiveSse stream = subscribe(actorB);
    ready(stream);

    jdbc.update(
        "UPDATE sales_ord.sales_order SET created_by = ? WHERE id = ?", actorA.id(), orderId);

    expectClosed(stream, LiveCloseReason.ACCESS_REVOKED);
  }

  @Test
  @DisplayName("L10: a deactivated user and a deleted order close ACCESS_REVOKED")
  void deactivationAndDeletionRevoke() {
    LiveSse reader = subscribe(actorB);
    LiveSse other = subscribe(actorC);
    ready(reader);
    ready(other);

    jdbc.update("UPDATE common_user.common_user SET is_active = false WHERE id = ?", actorB.id());
    expectClosed(reader, LiveCloseReason.ACCESS_REVOKED);
    expectQuiet(other);

    jdbc.update("UPDATE sales_ord.sales_order SET is_active = false WHERE id = ?", orderId);
    expectClosed(other, LiveCloseReason.ACCESS_REVOKED);
  }

  // ── helpers ──────────────────────────────────────────────────────────────

  /**
   * Changes one character in the middle of the signature, so the decoded signature bytes really
   * differ. Appending a character is not enough: when the signature length is a multiple of three
   * bytes (HS384 here), a lone trailing base64url character carries no full byte and is dropped by
   * the decoder, which leaves the original, valid signature.
   */
  private static String withBrokenSignature(String token) {
    int signatureStart = token.lastIndexOf('.') + 1;
    int target = signatureStart + (token.length() - signatureStart) / 2;
    char replacement = token.charAt(target) == 'A' ? 'B' : 'A';
    return token.substring(0, target) + replacement + token.substring(target + 1);
  }

  private LiveSse assertRefused(Map<String, String> headers, int status) {
    return assertRefused(port, orderId, headers, status);
  }

  private LiveSse assertRefused(
      int serverPort, UUID order, Map<String, String> headers, int status) {
    LiveSse stream = subscribe(serverPort, order, headers);
    assertThat(stream.status()).as(String.valueOf(stream.errorBody())).isEqualTo(status);
    assertThat(stream.headers().firstValue("Content-Type").orElse(""))
        .doesNotStartWith("text/event-stream");
    assertThat(stream.received()).isEmpty();
    awaitCondition(() -> liveRegistry.reservedCount() == liveRegistry.size());
    return stream;
  }
}
