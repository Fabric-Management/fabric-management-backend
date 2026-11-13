package com.fabricmanagement.notification.hub.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
@DisplayName("TenantNotificationDispatcher")
class TenantNotificationDispatcherTest {

  private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000000");
  private static final UUID REFERENCE = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final String EVENT = "FIBER_REQUEST_SUBMITTED";

  @Mock private NotificationHubService hub;

  private TenantNotificationDispatcher dispatcher;
  private Logger logger;
  private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

  @BeforeEach
  void setUp() {
    dispatcher =
        new TenantNotificationDispatcher(new TenantNotificationDispatcher.InTenantTransaction(hub));
    logger =
        (Logger) LoggerFactory.getLogger(TenantNotificationDispatcher.InTenantTransaction.class);
    logs.start();
    logger.addAppender(logs);
  }

  @AfterEach
  void tearDown() {
    logger.detachAppender(logs);
    logs.stop();
    TenantContext.clear();
  }

  @Test
  @DisplayName("no recipient: nothing is queued and the gap is logged as a warning")
  void warnsWhenNobodyIsToBeNotified() {
    dispatcher.notifyInTenant(
        TARGET, () -> List.<UUID>of(), EVENT, Map.of(), REFERENCE, "FIBER_REQUEST");

    verifyNoInteractions(hub);
    assertThat(warnings())
        .singleElement()
        .satisfies(
            message ->
                assertThat(message).contains(TARGET.toString(), EVENT, "nothing was queued"));
  }

  @Test
  @DisplayName("recipients reach the hub once each, in a stable order, without a warning")
  void handsDistinctSortedRecipientsToTheHub() {
    UUID first = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    UUID second = UUID.fromString("00000000-0000-0000-0000-00000000000b");
    Map<String, String> payload = Map.of("fiberName", "Modal");

    dispatcher.notifyInTenant(
        TARGET, () -> List.of(second, first, second), EVENT, payload, REFERENCE, "FIBER_REQUEST");

    verify(hub)
        .notifyAll(List.of(first, second), TARGET, EVENT, payload, REFERENCE, "FIBER_REQUEST");
    assertThat(warnings()).isEmpty();
  }

  @Test
  @DisplayName("recipients are looked up inside the target tenant, and the caller's is restored")
  void looksRecipientsUpInTheTargetTenant() {
    UUID caller = UUID.fromString("44444444-4444-4444-4444-444444444444");
    TenantContext.setCurrentTenantId(caller);
    AtomicReference<UUID> seenWhileLookingUp = new AtomicReference<>();

    dispatcher.notifyInTenant(
        TARGET,
        () -> {
          seenWhileLookingUp.set(TenantContext.getCurrentTenantIdOrNull());
          return List.<UUID>of();
        },
        EVENT,
        Map.of(),
        REFERENCE,
        "FIBER_REQUEST");

    assertThat(seenWhileLookingUp.get()).isEqualTo(TARGET);
    assertThat(TenantContext.getCurrentTenantIdOrNull()).isEqualTo(caller);
  }

  private List<String> warnings() {
    return logs.list.stream()
        .filter(event -> event.getLevel() == Level.WARN)
        .map(ILoggingEvent::getFormattedMessage)
        .toList();
  }
}
