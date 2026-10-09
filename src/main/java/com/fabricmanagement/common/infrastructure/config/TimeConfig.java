package com.fabricmanagement.common.infrastructure.config;

import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {

  /** The precision PostgreSQL keeps for {@code timestamp} and {@code timestamptz} values. */
  static final Duration DATABASE_PRECISION = Duration.ofNanos(1_000);

  /**
   * The application clock: UTC, ticking in microseconds. An instant answered from memory right
   * after a write is then the one a later read of the row returns; the system clock alone carries
   * nanoseconds on Linux. Only code that takes this bean is covered: {@code Instant.now()} called
   * directly keeps the system clock's resolution (docs/todo/time-precision-followup.md).
   */
  @Bean
  public Clock systemClock() {
    return Clock.tick(Clock.systemUTC(), DATABASE_PRECISION);
  }
}
