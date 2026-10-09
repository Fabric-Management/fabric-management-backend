package com.fabricmanagement.common.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The application clock keeps the precision of PostgreSQL's time columns. */
class TimeConfigTest {

  @Test
  @DisplayName("TP01: the application clock is the UTC system clock ticking in microseconds")
  void clockTicksInMicroseconds() {
    Clock clock = new TimeConfig().systemClock();

    assertThat(clock).isEqualTo(Clock.tick(Clock.systemUTC(), Duration.ofNanos(1_000)));
    assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
    for (int sample = 0; sample < 1_000; sample++) {
      Instant now = clock.instant();
      assertThat(now.getNano() % 1_000).as("sub-microsecond part of %s", now).isZero();
    }
  }
}
