package com.fabricmanagement.common.infrastructure.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tags;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class StuckEventMonitor {

  private static final String FIND_STUCK_SQL =
      """
      SELECT id, listener_id, event_type, serialized_event, publication_date
      FROM event_publication
      WHERE completion_date IS NULL AND publication_date < ?
      """;

  private static final String FIND_UNRESOLVED_MARKERS_SQL =
      """
      SELECT publication_id, event_type, listener_id, tenant_id, first_seen_at, resolved_at
      FROM public.stuck_event_publication
      WHERE resolved_at IS NULL
      """;

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;
  private final boolean enabled;
  private final long thresholdMinutes;
  private final MultiGauge stuckGauge;
  private final ObjectProvider<StuckEventHandler> handlerProvider;

  public StuckEventMonitor(
      JdbcTemplate jdbcTemplate,
      MeterRegistry meterRegistry,
      ObjectMapper objectMapper,
      ObjectProvider<StuckEventHandler> handlerProvider,
      @Value("${modulith.events.stuck-monitor.enabled:true}") boolean enabled,
      @Value("${modulith.events.stuck-monitor.threshold-minutes:10}") long thresholdMinutes) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
    this.handlerProvider = handlerProvider;
    this.enabled = enabled;
    this.thresholdMinutes = thresholdMinutes;
    this.stuckGauge =
        MultiGauge.builder("events.publication.stuck")
            .description("Number of stuck event publications grouped by event type")
            .register(meterRegistry);
  }

  @Scheduled(fixedDelayString = "${modulith.events.stuck-monitor.interval-ms:60000}")
  void sweep() {
    if (!enabled) {
      return;
    }

    try {
      Instant now = Instant.now();
      List<StuckRow> stuckRows = findStuckRows(now.minus(thresholdMinutes, ChronoUnit.MINUTES));
      Set<UUID> currentStuckIds = stuckRows.stream().map(StuckRow::id).collect(Collectors.toSet());
      Set<UUID> markedIds = findMarkedIds(currentStuckIds);

      stuckRows.stream()
          .filter(row -> !markedIds.contains(row.id()))
          .forEach(row -> reportNewlyStuck(row, now));

      findUnresolvedMarkers().stream()
          .filter(marker -> !currentStuckIds.contains(marker.publicationId()))
          .forEach(marker -> markResolved(marker, now));

      refreshGauge(stuckRows);
    } catch (Exception e) {
      log.warn("Failed to sweep stuck event publications", e);
    }
  }

  private List<StuckRow> findStuckRows(Instant threshold) {
    return jdbcTemplate.query(
        FIND_STUCK_SQL,
        (rs, rowNum) ->
            new StuckRow(
                rs.getObject("id", UUID.class),
                rs.getString("listener_id"),
                rs.getString("event_type"),
                rs.getString("serialized_event"),
                rs.getTimestamp("publication_date").toInstant()),
        Timestamp.from(threshold));
  }

  private Set<UUID> findMarkedIds(Set<UUID> publicationIds) {
    if (publicationIds.isEmpty()) {
      return Set.of();
    }

    String placeholders =
        publicationIds.stream().map(ignored -> "?").collect(Collectors.joining(","));
    String sql =
        "SELECT publication_id FROM public.stuck_event_publication WHERE publication_id IN ("
            + placeholders
            + ")";
    List<UUID> ids =
        jdbcTemplate.query(
            sql,
            (rs, rowNum) -> rs.getObject("publication_id", UUID.class),
            publicationIds.toArray());
    return new HashSet<>(ids);
  }

  private List<StuckMarker> findUnresolvedMarkers() {
    return jdbcTemplate.query(
        FIND_UNRESOLVED_MARKERS_SQL,
        (rs, rowNum) ->
            new StuckMarker(
                rs.getObject("publication_id", UUID.class),
                rs.getString("event_type"),
                rs.getString("listener_id"),
                rs.getObject("tenant_id", UUID.class),
                rs.getTimestamp("first_seen_at").toInstant(),
                toInstant(rs.getTimestamp("resolved_at"))));
  }

  private void reportNewlyStuck(StuckRow row, Instant now) {
    UUID tenantId = parseTenantId(row.payload());
    int inserted =
        jdbcTemplate.update(
            """
            INSERT INTO public.stuck_event_publication
                (publication_id, event_type, listener_id, tenant_id, first_seen_at, resolved_at)
            VALUES (?, ?, ?, ?, ?, NULL)
            ON CONFLICT (publication_id) DO NOTHING
            """,
            row.id(),
            row.eventType(),
            row.listenerId(),
            tenantId,
            Timestamp.from(now));

    if (inserted == 0) {
      return;
    }

    log.error(
        "Stuck event publication: eventType={} tenantId={} ageMinutes={} listenerId={} publicationId={}",
        row.eventType(),
        tenantId,
        Math.max(0, Duration.between(row.publicationDate(), now).toMinutes()),
        row.listenerId(),
        row.id());
    notifyNewlyStuckHandler(
        new StuckEventContext(
            row.id(), row.eventType(), row.listenerId(), tenantId, row.payload(), now));
  }

  private void markResolved(StuckMarker marker, Instant now) {
    int updated =
        jdbcTemplate.update(
            """
            UPDATE public.stuck_event_publication
            SET resolved_at = ?
            WHERE publication_id = ? AND resolved_at IS NULL
            """,
            Timestamp.from(now),
            marker.publicationId());

    if (updated == 0) {
      return;
    }

    log.info(
        "Stuck event publication resolved: eventType={} tenantId={} listenerId={} publicationId={}",
        marker.eventType(),
        marker.tenantId(),
        marker.listenerId(),
        marker.publicationId());
    notifyResolvedHandler(
        new StuckEventContext(
            marker.publicationId(),
            marker.eventType(),
            marker.listenerId(),
            marker.tenantId(),
            null,
            marker.firstSeenAt()));
  }

  private void refreshGauge(List<StuckRow> stuckRows) {
    Map<String, Long> countsByEventType =
        stuckRows.stream()
            .collect(Collectors.groupingBy(StuckRow::eventType, Collectors.counting()));
    stuckGauge.register(
        countsByEventType.entrySet().stream()
            .map(entry -> MultiGauge.Row.of(Tags.of("eventType", entry.getKey()), entry.getValue()))
            .toList(),
        true);
  }

  private UUID parseTenantId(String payload) {
    try {
      JsonNode root = objectMapper.readTree(payload);
      JsonNode tenantId = root == null ? null : root.get("tenantId");
      if (tenantId == null || tenantId.isNull()) {
        return null;
      }
      return UUID.fromString(tenantId.asText());
    } catch (JsonProcessingException | IllegalArgumentException e) {
      return null;
    }
  }

  private Instant toInstant(Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toInstant();
  }

  private void notifyNewlyStuckHandler(StuckEventContext context) {
    try {
      handlerProvider.ifAvailable(handler -> handler.onNewlyStuck(context));
    } catch (Exception exception) {
      log.warn(
          "Failed to handle newly stuck event publication: publicationId={}",
          context.publicationId(),
          exception);
    }
  }

  private void notifyResolvedHandler(StuckEventContext context) {
    try {
      handlerProvider.ifAvailable(handler -> handler.onResolved(context));
    } catch (Exception exception) {
      log.warn(
          "Failed to handle resolved event publication: publicationId={}",
          context.publicationId(),
          exception);
    }
  }

  record StuckRow(
      UUID id, String listenerId, String eventType, String payload, Instant publicationDate) {}

  protected record StuckMarker(
      UUID publicationId,
      String eventType,
      String listenerId,
      UUID tenantId,
      Instant firstSeenAt,
      Instant resolvedAt) {}
}
