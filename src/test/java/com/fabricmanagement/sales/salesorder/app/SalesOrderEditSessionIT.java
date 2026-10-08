package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.platform.realtime.app.LiveEditSessionRetentionJob;
import com.fabricmanagement.platform.realtime.domain.exception.LiveEditSessionNotFoundException;
import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditSessionDto;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditorDto;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditorsDto;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;

/**
 * Edit sessions of a sales order (CEDIT-06): who holds the edit form open, on real PostgreSQL and a
 * real HTTP server, and how the live stream signals a change of it. Sessions are presence only; no
 * save depends on them, so none is exercised here.
 */
class SalesOrderEditSessionIT extends SalesOrderLiveItSupport {

  private static final HttpClient HTTP =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

  @Autowired private SalesOrderEditSessionService sessions;
  @Autowired private LiveEditSessionRetentionJob retention;

  @Test
  @DisplayName("E01: an opened session is listed; others see the person, never the session id")
  void openedSessionIsListed() {
    SalesOrderEditSessionDto mine = openSession(actorA);

    assertThat(mine.renewAfterSeconds()).isEqualTo(30);
    assertThat(mine.expiresAt()).isAfter(Instant.now());

    SalesOrderEditorDto own = only(editors(actorA));
    assertThat(own.mine()).isTrue();
    assertThat(own.editSessionId()).isEqualTo(mine.editSessionId());
    assertThat(own.userId()).isEqualTo(actorA.id());

    SalesOrderEditorDto seen = only(editors(actorB));
    assertThat(seen.mine()).isFalse();
    assertThat(seen.editSessionId()).isNull();
    assertThat(seen.userId()).isEqualTo(actorA.id());
    assertThat(seen.displayName()).contains("Avery");
  }

  @Test
  @DisplayName("E02 (CE-10): two tabs of one person are two sessions")
  void twoTabsAreTwoSessions() {
    UUID first = openSession(actorA).editSessionId();
    UUID second = openSession(actorA).editSessionId();

    assertThat(first).isNotEqualTo(second);
    assertThat(editors(actorA).editors())
        .extracting(SalesOrderEditorDto::editSessionId)
        .containsExactly(first, second);
    assertThat(editors(actorB).editors())
        .hasSize(2)
        .allSatisfy(editor -> assertThat(editor.editSessionId()).isNull());
  }

  @Test
  @DisplayName("E03 (CE-08): an unrenewed session drops out; a late renewal cannot revive it")
  void unrenewedSessionExpires() {
    UUID session = openSession(actorA).editSessionId();
    clock.advance(Duration.ofSeconds(60));
    SalesOrderEditSessionDto renewed = renewSession(actorA, session);
    assertThat(renewed.expiresAt()).isAfter(clock.instant().plusSeconds(80));

    clock.advance(Duration.ofSeconds(91));
    assertThat(editors(actorB).editors()).isEmpty();
    assertThat(as(actorA, () -> sessions.renew(orderId, session, actorA.id())))
        .isInstanceOf(LiveEditSessionNotFoundException.class);
  }

  @Test
  @DisplayName("E04 (CE-17): nobody renews or closes another person's session")
  void othersCannotTouchMySession() {
    UUID session = openSession(actorA).editSessionId();

    assertThat(as(actorB, () -> sessions.renew(orderId, session, actorB.id())))
        .isInstanceOf(LiveEditSessionNotFoundException.class);
    as(actorB, () -> run(() -> sessions.close(orderId, session, actorB.id())));

    assertThat(only(editors(actorB)).userId()).isEqualTo(actorA.id());
  }

  @Test
  @DisplayName("E05: a closed session is gone; closing again changes nothing")
  void closedSessionIsGone() {
    UUID session = openSession(actorA).editSessionId();

    as(actorA, () -> run(() -> sessions.close(orderId, session, actorA.id())));
    as(actorA, () -> run(() -> sessions.close(orderId, session, actorA.id())));

    assertThat(editors(actorB).editors()).isEmpty();
    assertThat(as(actorA, () -> sessions.renew(orderId, session, actorA.id())))
        .isInstanceOf(LiveEditSessionNotFoundException.class);
  }

  @Test
  @DisplayName("E06: a read-only user sees who edits but cannot open a session")
  void readOnlyUserListsButCannotOpen() {
    readOnlyUsers.add(actorC.id());
    openSession(actorA);

    assertThat(as(actorC, () -> sessions.open(orderId, actorC.id())))
        .isInstanceOf(AccessDeniedException.class);
    assertThat(only(editors(actorC)).userId()).isEqualTo(actorA.id());
  }

  @Test
  @DisplayName("E07 (CE-17): no access, an out-of-scope order and another tenant are not found")
  void hiddenOrdersAreNotFound() {
    openSession(actorA);

    revoke(actorC);
    assertThat(as(actorC, () -> sessions.editors(orderId, actorC.id())))
        .isInstanceOf(NotFoundException.class);
    assertThat(as(actorC, () -> sessions.open(orderId, actorC.id())))
        .isInstanceOf(NotFoundException.class);

    scopes.put(actorB.id(), DataScope.OWN);
    assertThat(as(actorB, () -> sessions.editors(orderId, actorB.id())))
        .isInstanceOf(NotFoundException.class);

    OtherTenant other = otherTenantWithOrder();
    assertThat(as(other.actor(), () -> sessions.editors(orderId, other.actor().id())))
        .isInstanceOf(NotFoundException.class);
    assertThat(as(other.actor(), () -> sessions.open(orderId, other.actor().id())))
        .isInstanceOf(NotFoundException.class);
    SalesOrderEditorsDto theirs =
        (SalesOrderEditorsDto)
            as(other.actor(), () -> sessions.editors(other.orderId(), other.actor().id()));
    assertThat(theirs.editors()).isEmpty();
  }

  @Test
  @DisplayName("E08 (CE-18): open, close and expiry change the stream's presence marker only")
  void streamSignalsPresence() {
    LiveSse stream = subscribe(actorB);
    LiveSse.Frame first = ready(stream);
    String version = first.revision();
    assertThat(first.body().path("presenceRevision").asText()).isEqualTo("0");

    UUID session = openSession(actorA).editSessionId();
    LiveSse.Frame opened = stream.nextEvent(WAIT);
    assertThat(opened.event()).isEqualTo("invalidated");
    assertThat(opened.revision()).isEqualTo(version);
    String present = opened.body().path("presenceRevision").asText();
    assertThat(present).isNotEqualTo("0").startsWith("p");

    // A renewal changes nobody's presence: no frame.
    renewSession(actorA, session);
    expectQuiet(stream);

    as(actorA, () -> run(() -> sessions.close(orderId, session, actorA.id())));
    LiveSse.Frame closed = stream.nextEvent(WAIT);
    assertThat(closed.revision()).isEqualTo(version);
    assertThat(closed.body().path("presenceRevision").asText()).isEqualTo("0");

    // Expiry is noticed without any write.
    UUID abandoned = openSession(actorA).editSessionId();
    assertThat(stream.nextEvent(WAIT).body().path("presenceRevision").asText()).isNotEqualTo("0");
    jdbc.update(
        "UPDATE common_infrastructure.live_edit_session SET last_seen_at = now() - interval"
            + " '10 minutes', expires_at = now() - interval '5 minutes' WHERE id = ?",
        abandoned);
    assertThat(stream.nextEvent(WAIT).body().path("presenceRevision").asText()).isEqualTo("0");
  }

  @Test
  @DisplayName("E09: the HTTP contract: 201, 200, 204, 404 for an ended session, 403 read-only")
  void httpContract() throws Exception {
    readOnlyUsers.add(actorC.id());
    String base = "/api/v1/sales/orders/" + orderId + "/edit-sessions";

    Answer opened = http("POST", base, actorA);
    assertThat(opened.status()).isEqualTo(201);
    String id = opened.body().path("data").path("editSessionId").asText();
    assertThat(opened.body().path("data").path("renewAfterSeconds").asLong()).isEqualTo(30);

    Answer listed = http("GET", base, actorB);
    assertThat(listed.status()).isEqualTo(200);
    JsonNode editor = listed.body().path("data").path("editors").get(0);
    assertThat(editor.path("mine").asBoolean()).isFalse();
    assertThat(editor.path("editSessionId").isNull()).isTrue();

    assertThat(http("PUT", base + "/" + id, actorA).status()).isEqualTo(200);
    assertThat(http("POST", base, actorC).status()).isEqualTo(403);
    assertThat(http("GET", base, actorC).status()).isEqualTo(200);
    assertThat(http("DELETE", base + "/" + id, actorA).status()).isEqualTo(204);
    assertThat(http("DELETE", base + "/" + id, actorA).status()).isEqualTo(204);

    Answer ended = http("PUT", base + "/" + id, actorA);
    assertThat(ended.status()).isEqualTo(404);
    assertThat(ended.body().path("code").asText()).isEqualTo("EDIT_SESSION_NOT_FOUND");
    assertThat(
            http("GET", "/api/v1/sales/orders/" + UUID.randomUUID() + "/edit-sessions", actorA)
                .status())
        .isEqualTo(404);
  }

  @Test
  @DisplayName("E10: retention deletes ended sessions past the window and keeps live ones")
  void retentionDeletesEndedSessions() {
    UUID closed = openSession(actorA).editSessionId();
    as(actorA, () -> run(() -> sessions.close(orderId, closed, actorA.id())));
    UUID expired = openSession(actorB).editSessionId();
    UUID live = openSession(actorC).editSessionId();
    jdbc.update(
        "UPDATE common_infrastructure.live_edit_session SET closed_at = now() - interval '8 days'"
            + " WHERE id = ?",
        closed);
    jdbc.update(
        "UPDATE common_infrastructure.live_edit_session SET last_seen_at = now() - interval"
            + " '9 days', expires_at = now() - interval '8 days' WHERE id = ?",
        expired);

    assertThat(retention.purge(Instant.now())).isEqualTo(2);
    assertThat(sessionIds()).contains(live).doesNotContain(closed, expired);
    assertThat(retention.purge(Instant.now())).isZero();
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  private SalesOrderEditSessionDto openSession(Actor actor) {
    return (SalesOrderEditSessionDto) succeed(as(actor, () -> sessions.open(orderId, actor.id())));
  }

  private SalesOrderEditSessionDto renewSession(Actor actor, UUID session) {
    return (SalesOrderEditSessionDto)
        succeed(as(actor, () -> sessions.renew(orderId, session, actor.id())));
  }

  private SalesOrderEditorsDto editors(Actor actor) {
    return (SalesOrderEditorsDto) succeed(as(actor, () -> sessions.editors(orderId, actor.id())));
  }

  private static SalesOrderEditorDto only(SalesOrderEditorsDto editors) {
    assertThat(editors.editors()).hasSize(1);
    return editors.editors().getFirst();
  }

  private static Object succeed(Object result) {
    if (result instanceof RuntimeException failure) {
      throw new AssertionError("Expected a result but got " + failure, failure);
    }
    return result;
  }

  private static Boolean run(Runnable step) {
    step.run();
    return Boolean.TRUE;
  }

  private List<UUID> sessionIds() {
    return jdbc.queryForList(
        "SELECT id FROM common_infrastructure.live_edit_session WHERE tenant_id = ?",
        UUID.class,
        tenantId);
  }

  private record Answer(int status, JsonNode body) {}

  private Answer http(String method, String path, Actor actor)
      throws IOException, InterruptedException {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .timeout(Duration.ofSeconds(10))
            .header("Authorization", "Bearer " + token(actor))
            .header("Content-Type", "application/json")
            .method(method, HttpRequest.BodyPublishers.noBody())
            .build();
    HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    String text = response.body();
    JsonNode body = text == null || text.isBlank() ? null : objectMapper.readTree(text);
    return new Answer(response.statusCode(), body);
  }
}
