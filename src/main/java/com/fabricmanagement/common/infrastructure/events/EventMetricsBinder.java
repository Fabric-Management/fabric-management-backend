package com.fabricmanagement.common.infrastructure.events;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Binds incomplete event publications metric to Micrometer. Exposes a gauge showing how many events
 * are currently sitting in the outbox waiting to be successfully published.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventMetricsBinder implements MeterBinder {

  private final JdbcTemplate jdbcTemplate;
  private final AtomicInteger incompleteCount = new AtomicInteger(0);

  @Override
  public void bindTo(MeterRegistry registry) {
    Gauge.builder("events.publications.incomplete", incompleteCount, AtomicInteger::get)
        .description("Number of incomplete domain event publications older than 60s (Outbox/DLQ)")
        .register(registry);
  }

  @Scheduled(fixedDelayString = "${modulith.events.resubmit.interval-ms:300000}")
  public void updateIncompleteEventsCount() {
    try {
      // Sadece 60 saniyeden eski, takili kalan event'leri (incomplete) veritabanindan hizlica say.
      Instant threshold = Instant.now().minus(60, ChronoUnit.SECONDS);

      String sql =
          "SELECT COUNT(*) FROM event_publication WHERE completion_date IS NULL AND publication_date < ?";
      Integer count = jdbcTemplate.queryForObject(sql, Integer.class, Timestamp.from(threshold));

      if (count != null) {
        incompleteCount.set(count);
        log.debug("Updated incomplete events gauge cache to {}", count);
      }
    } catch (Exception e) {
      log.warn("Failed to query incomplete events count for gauge", e);
    }
  }
}
