package com.fabricmanagement.common.infrastructure.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class StuckEventMonitorTest {

  @Mock private JdbcTemplate jdbcTemplate;
  @Mock private ObjectProvider<StuckEventHandler> handlerProvider;
  @Mock private StuckEventHandler stuckEventHandler;

  private SimpleMeterRegistry meterRegistry;
  private StuckEventMonitor monitor;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    lenient()
        .doAnswer(
            invocation -> {
              Consumer<StuckEventHandler> consumer = invocation.getArgument(0);
              consumer.accept(stuckEventHandler);
              return null;
            })
        .when(handlerProvider)
        .ifAvailable(any());
    monitor =
        new StuckEventMonitor(
            jdbcTemplate, meterRegistry, new ObjectMapper(), handlerProvider, true, 10);
  }

  @Test
  void reportsNewlyStuckPublicationAndRefreshesGauge(CapturedOutput output) {
    UUID publicationId = UUID.randomUUID();
    UUID tenantId = UUID.randomUUID();
    StuckEventMonitor.StuckRow row = stuckRow(publicationId, tenantId.toString());
    stubSweep(List.of(row), List.of(), List.of());
    when(jdbcTemplate.update(
            startsWith("INSERT INTO public.stuck_event_publication"), any(Object[].class)))
        .thenReturn(1);

    monitor.sweep();

    verify(jdbcTemplate)
        .update(startsWith("INSERT INTO public.stuck_event_publication"), any(Object[].class));
    assertThat(output.getAll()).contains("Stuck event publication: eventType=");
    assertThat(
            meterRegistry
                .get("events.publication.stuck")
                .tag("eventType", row.eventType())
                .gauge()
                .value())
        .isEqualTo(1.0);
  }

  @Test
  void doesNotInsertOrLogSamePublicationTwice(CapturedOutput output) {
    UUID publicationId = UUID.randomUUID();
    StuckEventMonitor.StuckRow row = stuckRow(publicationId, UUID.randomUUID().toString());
    stubStuckRows(List.of(row));
    when(jdbcTemplate.query(
            startsWith("SELECT publication_id FROM public.stuck_event_publication WHERE"),
            ArgumentMatchers.<RowMapper<UUID>>any(),
            any(Object[].class)))
        .thenReturn(List.of(), List.of(publicationId));
    stubUnresolvedMarkers(List.of());
    when(jdbcTemplate.update(
            startsWith("INSERT INTO public.stuck_event_publication"), any(Object[].class)))
        .thenReturn(1);

    monitor.sweep();
    monitor.sweep();

    verify(jdbcTemplate, times(1))
        .update(startsWith("INSERT INTO public.stuck_event_publication"), any(Object[].class));
    assertThat(occurrences(output.getAll(), "Stuck event publication: eventType=")).isEqualTo(1);
  }

  @Test
  void marksPreviouslyStuckPublicationAsResolved(CapturedOutput output) {
    UUID publicationId = UUID.randomUUID();
    StuckEventMonitor.StuckMarker marker =
        new StuckEventMonitor.StuckMarker(
            publicationId,
            "com.fabricmanagement.SomeEvent",
            "listener",
            UUID.randomUUID(),
            Instant.now().minusSeconds(900),
            null);
    stubSweep(List.of(), List.of(), List.of(marker));
    when(jdbcTemplate.update(
            startsWith("UPDATE public.stuck_event_publication"), any(Object[].class)))
        .thenReturn(1);

    monitor.sweep();

    verify(jdbcTemplate)
        .update(startsWith("UPDATE public.stuck_event_publication"), any(Object[].class));
    assertThat(output.getAll()).contains("Stuck event publication resolved: eventType=");
  }

  @Test
  void reportsPayloadsWithMissingOrGarbledTenantAsUnknown() {
    StuckEventMonitor.StuckRow missingTenant = stuckRow(UUID.randomUUID(), null);
    StuckEventMonitor.StuckRow garbledTenant =
        new StuckEventMonitor.StuckRow(
            UUID.randomUUID(),
            "listener",
            "com.fabricmanagement.SomeEvent",
            "{\"tenantId\":\"not-a-uuid\"}",
            Instant.now().minusSeconds(900));
    stubSweep(List.of(missingTenant, garbledTenant), List.of(), List.of());
    when(jdbcTemplate.update(
            startsWith("INSERT INTO public.stuck_event_publication"), any(Object[].class)))
        .thenReturn(1);

    monitor.sweep();

    ArgumentCaptor<Object[]> arguments = ArgumentCaptor.forClass(Object[].class);
    verify(jdbcTemplate, times(2))
        .update(startsWith("INSERT INTO public.stuck_event_publication"), arguments.capture());
    assertThat(arguments.getAllValues()).allSatisfy(values -> assertThat(values[3]).isNull());
  }

  @Test
  void disabledMonitorDoesNotAccessDatabaseOrRegisterGauge() {
    monitor =
        new StuckEventMonitor(
            jdbcTemplate, meterRegistry, new ObjectMapper(), handlerProvider, false, 10);

    monitor.sweep();

    verifyNoInteractions(jdbcTemplate);
    assertThat(meterRegistry.find("events.publication.stuck").meter()).isNull();
  }

  @Test
  void jdbcFailureIsLoggedAndSwallowed(CapturedOutput output) {
    when(jdbcTemplate.query(
            startsWith("SELECT id, listener_id, event_type"),
            ArgumentMatchers.<RowMapper<StuckEventMonitor.StuckRow>>any(),
            any(Object[].class)))
        .thenThrow(new RuntimeException("database unavailable"));

    assertThatCode(monitor::sweep).doesNotThrowAnyException();

    assertThat(output.getAll()).contains("Failed to sweep stuck event publications");
  }

  @Test
  void insertRaceGuardDoesNotLogDuplicateStuckError(CapturedOutput output) {
    StuckEventMonitor.StuckRow row = stuckRow(UUID.randomUUID(), UUID.randomUUID().toString());
    stubSweep(List.of(row), List.of(), List.of());
    when(jdbcTemplate.update(
            startsWith("INSERT INTO public.stuck_event_publication"), any(Object[].class)))
        .thenReturn(0);

    monitor.sweep();

    assertThat(output.getAll()).doesNotContain("Stuck event publication: eventType=");
  }

  @Test
  void updateRaceGuardDoesNotLogDuplicateResolution(CapturedOutput output) {
    StuckEventMonitor.StuckMarker marker =
        new StuckEventMonitor.StuckMarker(
            UUID.randomUUID(),
            "com.fabricmanagement.SomeEvent",
            "listener",
            UUID.randomUUID(),
            Instant.now().minusSeconds(900),
            null);
    stubSweep(List.of(), List.of(), List.of(marker));
    when(jdbcTemplate.update(
            startsWith("UPDATE public.stuck_event_publication"), any(Object[].class)))
        .thenReturn(0);

    monitor.sweep();

    assertThat(output.getAll()).doesNotContain("Stuck event publication resolved:");
  }

  @Test
  void gaugeOverwritesChangedCountAndDropsDisappearedEventType() {
    StuckEventMonitor.StuckRow first =
        new StuckEventMonitor.StuckRow(
            UUID.randomUUID(), "listener-1", "A", "{}", Instant.now().minusSeconds(900));
    StuckEventMonitor.StuckRow second =
        new StuckEventMonitor.StuckRow(
            UUID.randomUUID(), "listener-2", "A", "{}", Instant.now().minusSeconds(900));
    when(jdbcTemplate.query(
            startsWith("SELECT id, listener_id, event_type"),
            ArgumentMatchers.<RowMapper<StuckEventMonitor.StuckRow>>any(),
            any(Object[].class)))
        .thenReturn(List.of(first, second), List.of(first), List.of());
    when(jdbcTemplate.query(
            startsWith("SELECT publication_id FROM public.stuck_event_publication WHERE"),
            ArgumentMatchers.<RowMapper<UUID>>any(),
            any(Object[].class)))
        .thenReturn(List.of(first.id(), second.id()), List.of(first.id()));
    stubUnresolvedMarkers(List.of());

    monitor.sweep();
    monitor.sweep();

    assertThat(meterRegistry.find("events.publication.stuck").tag("eventType", "A").gauge().value())
        .isEqualTo(1.0);

    monitor.sweep();

    assertThat(meterRegistry.find("events.publication.stuck").tag("eventType", "A").gauge())
        .isNull();
  }

  @Test
  void handlerIsInvokedOnlyAfterWinningInsertAndResolutionRaces() {
    UUID tenantId = UUID.randomUUID();
    StuckEventMonitor.StuckRow insertedRow = stuckRow(UUID.randomUUID(), tenantId.toString());
    stubSweep(List.of(insertedRow), List.of(), List.of());
    when(jdbcTemplate.update(
            startsWith("INSERT INTO public.stuck_event_publication"), any(Object[].class)))
        .thenReturn(1);

    monitor.sweep();

    StuckEventMonitor.StuckMarker updatedMarker =
        new StuckEventMonitor.StuckMarker(
            insertedRow.id(),
            insertedRow.eventType(),
            insertedRow.listenerId(),
            tenantId,
            Instant.now().minusSeconds(60),
            null);
    stubSweep(List.of(), List.of(), List.of(updatedMarker));
    when(jdbcTemplate.update(
            startsWith("UPDATE public.stuck_event_publication"), any(Object[].class)))
        .thenReturn(1);

    monitor.sweep();

    StuckEventMonitor.StuckRow lostInsertRace = stuckRow(UUID.randomUUID(), tenantId.toString());
    stubSweep(List.of(lostInsertRace), List.of(), List.of());
    when(jdbcTemplate.update(
            startsWith("INSERT INTO public.stuck_event_publication"), any(Object[].class)))
        .thenReturn(0);

    monitor.sweep();

    StuckEventMonitor.StuckMarker lostUpdateRace =
        new StuckEventMonitor.StuckMarker(
            UUID.randomUUID(),
            insertedRow.eventType(),
            insertedRow.listenerId(),
            tenantId,
            Instant.now().minusSeconds(60),
            null);
    stubSweep(List.of(), List.of(), List.of(lostUpdateRace));
    when(jdbcTemplate.update(
            startsWith("UPDATE public.stuck_event_publication"), any(Object[].class)))
        .thenReturn(0);

    monitor.sweep();

    verify(stuckEventHandler, times(1)).onNewlyStuck(any(StuckEventContext.class));
    verify(stuckEventHandler, times(1)).onResolved(any(StuckEventContext.class));
    verifyNoMoreInteractions(stuckEventHandler);
  }

  private void stubSweep(
      List<StuckEventMonitor.StuckRow> stuckRows,
      List<UUID> markedIds,
      List<StuckEventMonitor.StuckMarker> unresolvedMarkers) {
    stubStuckRows(stuckRows);
    if (!stuckRows.isEmpty()) {
      when(jdbcTemplate.query(
              startsWith("SELECT publication_id FROM public.stuck_event_publication WHERE"),
              ArgumentMatchers.<RowMapper<UUID>>any(),
              any(Object[].class)))
          .thenReturn(markedIds);
    }
    stubUnresolvedMarkers(unresolvedMarkers);
  }

  private void stubStuckRows(List<StuckEventMonitor.StuckRow> stuckRows) {
    when(jdbcTemplate.query(
            startsWith("SELECT id, listener_id, event_type"),
            ArgumentMatchers.<RowMapper<StuckEventMonitor.StuckRow>>any(),
            any(Object[].class)))
        .thenReturn(stuckRows);
  }

  private void stubUnresolvedMarkers(List<StuckEventMonitor.StuckMarker> unresolvedMarkers) {
    when(jdbcTemplate.query(
            startsWith("SELECT publication_id, event_type"),
            ArgumentMatchers.<RowMapper<StuckEventMonitor.StuckMarker>>any()))
        .thenReturn(unresolvedMarkers);
  }

  private StuckEventMonitor.StuckRow stuckRow(UUID publicationId, String tenantId) {
    String payload = tenantId == null ? "{}" : "{\"tenantId\":\"" + tenantId + "\"}";
    return new StuckEventMonitor.StuckRow(
        publicationId,
        "listener",
        "com.fabricmanagement.SomeEvent",
        payload,
        Instant.now().minusSeconds(900));
  }

  private int occurrences(String value, String needle) {
    return (value.length() - value.replace(needle, "").length()) / needle.length();
  }
}
