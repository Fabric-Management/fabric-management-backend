package com.fabricmanagement.platform.realtime.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Field lease keys and ownership periods (CEDIT-07 §3.1–3.2), without a database. */
class LiveEditLeaseTest {

  private static final Instant NOW = Instant.parse("2026-10-08T10:00:00Z");
  private static final LiveResource ORDER = new LiveResource("sales-order", UUID.randomUUID());
  private static final LiveLeaseKey NOTES = new LiveLeaseKey("header", "notes");

  @Test
  @DisplayName("L03: a whole scope overlaps its parts; different parts and scopes never overlap")
  void overlap() {
    LiveLeaseKey line = LiveLeaseKey.whole("line:" + UUID.randomUUID());
    LiveLeaseKey quantity = new LiveLeaseKey(line.scope(), "line.quantity");
    LiveLeaseKey pricing = new LiveLeaseKey(line.scope(), "line.pricing");
    LiveLeaseKey otherLine = new LiveLeaseKey("line:" + UUID.randomUUID(), "line.quantity");

    assertThat(line.overlaps(quantity)).isTrue();
    assertThat(quantity.overlaps(line)).isTrue();
    assertThat(quantity.overlaps(quantity)).isTrue();
    assertThat(quantity.overlaps(pricing)).isFalse();
    assertThat(quantity.overlaps(otherLine)).isFalse();
    assertThat(NOTES.overlaps(new LiveLeaseKey("header", "paymentTerms"))).isFalse();
    assertThat(line.compareTo(quantity)).isNegative();
  }

  @Test
  @DisplayName("L03/L19: free paths, blanks and odd characters are not keys")
  void keysAreValidated() {
    assertThatThrownBy(() -> new LiveLeaseKey("header", "lines[0].quantity"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new LiveLeaseKey("Header", "notes"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new LiveLeaseKey("header", " "))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new LiveLeaseKey(null, "notes"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(LiveLeaseKey.whole("header").isWhole()).isTrue();
  }

  @Test
  @DisplayName("L05: every new period has a new token, the same session's included")
  void everyPeriodHasItsOwnToken() {
    UUID session = UUID.randomUUID();
    UUID user = UUID.randomUUID();
    LiveEditLease lease =
        LiveEditLease.grant(ORDER, NOTES, 0, session, user, NOW, NOW.plusSeconds(90));
    UUID first = lease.getToken();
    assertThat(lease.isHeldBy(session, NOW.plusSeconds(89), 0)).isTrue();

    lease.release(NOW.plusSeconds(10));
    assertThat(lease.isHeldAt(NOW.plusSeconds(11), 0)).isFalse();
    lease.takeOver(0, session, user, NOW.plusSeconds(20), NOW.plusSeconds(110));
    assertThat(lease.getToken()).isNotEqualTo(first);
    assertThat(lease.getAcquiredAt()).isEqualTo(NOW.plusSeconds(20));
    assertThat(lease.getReleasedAt()).isNull();

    UUID second = lease.getToken();
    UUID other = UUID.randomUUID();
    lease.takeOver(0, other, UUID.randomUUID(), NOW.plusSeconds(200), NOW.plusSeconds(290));
    assertThat(lease.getToken()).isNotIn(first, second);
    assertThat(lease.getSessionId()).isEqualTo(other);
  }

  @Test
  @DisplayName("L05: an ended lease is never renewed; a held one is never taken over")
  void endedLeasesStayEnded() {
    UUID session = UUID.randomUUID();
    LiveEditLease lease =
        LiveEditLease.grant(ORDER, NOTES, 0, session, UUID.randomUUID(), NOW, NOW.plusSeconds(90));

    assertThatThrownBy(
            () -> lease.takeOver(0, UUID.randomUUID(), UUID.randomUUID(), NOW, NOW.plusSeconds(5)))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(
            () -> lease.renew(NOW.plusSeconds(90), NOW.plusSeconds(180), NOW.plusSeconds(3600), 0))
        .isInstanceOf(IllegalStateException.class);
    UUID token = lease.getToken();
    assertThat(lease.renew(NOW.plusSeconds(30), NOW.plusSeconds(120), NOW.plusSeconds(3600), 0))
        .isTrue();
    assertThat(lease.getToken()).isEqualTo(token);
    assertThat(lease.getExpiresAt()).isEqualTo(NOW.plusSeconds(120));
    assertThat(lease.getAcquiredAt()).isEqualTo(NOW);
  }

  @Test
  @DisplayName("L16: a lease of an older resource generation is not held, and cannot be renewed")
  void anotherGenerationVoidsTheLease() {
    UUID session = UUID.randomUUID();
    LiveEditLease lease =
        LiveEditLease.grant(ORDER, NOTES, 3, session, UUID.randomUUID(), NOW, NOW.plusSeconds(90));

    assertThat(lease.isHeldBy(session, NOW.plusSeconds(1), 3)).isTrue();
    assertThat(lease.isHeldBy(session, NOW.plusSeconds(1), 4)).isFalse();
    assertThatThrownBy(
            () -> lease.renew(NOW.plusSeconds(1), NOW.plusSeconds(91), NOW.plusSeconds(3600), 4))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  @DisplayName(
      "R3: a renewal or release from a clock behind the recorded times never moves them backwards"
          + " and never passes the session bound; with no consistent extension the period ends")
  void timesNeverMoveBackwards() {
    UUID session = UUID.randomUUID();
    // Granted by an instance whose clock is 5 s ahead of this one.
    Instant ahead = NOW.plusSeconds(5);
    LiveEditLease lease =
        LiveEditLease.grant(
            ORDER, NOTES, 0, session, UUID.randomUUID(), ahead, ahead.plusSeconds(90));
    UUID token = lease.getToken();

    assertThat(lease.renew(NOW, NOW.plusSeconds(90), NOW.plusSeconds(600), 0)).isTrue();
    assertThat(lease.getRenewedAt()).isEqualTo(ahead);
    assertThat(lease.getRenewedAt()).isAfterOrEqualTo(lease.getAcquiredAt());
    assertThat(lease.getExpiresAt()).isEqualTo(ahead.plusSeconds(90));
    assertThat(lease.getToken()).isEqualTo(token);

    // The session bound caps the expiry even when the recorded one was later.
    assertThat(lease.renew(NOW, NOW.plusSeconds(90), NOW.plusSeconds(60), 0)).isTrue();
    assertThat(lease.getExpiresAt()).isEqualTo(NOW.plusSeconds(60));
    assertThat(lease.getExpiresAt()).isAfter(lease.getRenewedAt());

    // No consistent extension: the period ends here, on the server, not only for the caller
    // (R3 follow-up). The recorded times stay consistent and the token proves nothing any more.
    Instant renewed = lease.getRenewedAt();
    assertThat(lease.renew(NOW, NOW.plusSeconds(90), NOW.plusSeconds(1), 0)).isFalse();
    assertThat(lease.getReleasedAt()).isEqualTo(ahead);
    assertThat(lease.isHeldAt(NOW, 0)).isFalse();
    assertThat(lease.isHeldBy(session, NOW, 0)).isFalse();
    assertThat(lease.getRenewedAt()).isEqualTo(renewed);
    assertThat(lease.getExpiresAt()).isEqualTo(NOW.plusSeconds(60));
    assertThat(lease.getToken()).isEqualTo(token);

    // Ended, so another renewal is refused, a release changes nothing, and a new period (the same
    // session's included) starts on this clock with a new token.
    assertThatThrownBy(() -> lease.renew(NOW, NOW.plusSeconds(90), NOW.plusSeconds(600), 0))
        .isInstanceOf(IllegalStateException.class);
    lease.release(NOW.plusSeconds(30));
    assertThat(lease.getReleasedAt()).isEqualTo(ahead);
    lease.takeOver(0, session, lease.getUserId(), NOW, NOW.plusSeconds(90));
    assertThat(lease.getToken()).isNotEqualTo(token);
    assertThat(lease.getAcquiredAt()).isEqualTo(NOW);
    assertThat(lease.getRenewedAt()).isEqualTo(NOW);
    assertThat(lease.getReleasedAt()).isNull();
  }

  @Test
  @DisplayName("L19: a period lives for a positive time only")
  void positiveLifetime() {
    assertThatThrownBy(
            () ->
                LiveEditLease.grant(
                    ORDER, NOTES, 0, UUID.randomUUID(), UUID.randomUUID(), NOW, NOW))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
