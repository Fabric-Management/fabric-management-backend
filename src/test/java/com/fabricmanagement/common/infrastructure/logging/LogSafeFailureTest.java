package com.fabricmanagement.common.infrastructure.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LogSafeFailureTest {
  @Test
  void snapshotKeepsSourceFramesButDoesNotRetainAnyPayloadException() {
    var cause = new IllegalArgumentException("Hawthorn!Secret27");
    var original = new IllegalStateException("https://app.example.com/setup?token=secret", cause);
    original.addSuppressed(new RuntimeException("secret"));
    var safe = LogSafeFailure.snapshot(original);
    assertThat(safe.getMessage()).isEqualTo("failureType=java.lang.IllegalStateException");
    assertThat(safe.getStackTrace()).containsExactly(original.getStackTrace());
    assertThat(safe.getCause()).isNull();
    assertThat(safe.getSuppressed()).isEmpty();
    assertThat(original.getCause()).isSameAs(cause);
    assertThat(original.getMessage()).contains("token=secret");
  }
}
