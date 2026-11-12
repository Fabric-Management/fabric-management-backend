package com.fabricmanagement.notification.hub.app.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.events.IdempotentEventHandler;
import com.fabricmanagement.common.infrastructure.events.ProcessedEventRepository;
import com.fabricmanagement.common.infrastructure.security.PermissionKey;
import com.fabricmanagement.notification.hub.app.NotificationHubService;
import com.fabricmanagement.notification.hub.domain.NotificationEventType;
import com.fabricmanagement.notification.hub.domain.port.PermissionRecipientPort;
import com.fabricmanagement.production.core.batch.domain.event.BatchCertificationsExpiringEvent;
import com.fabricmanagement.production.quality.result.domain.event.BatchConcessionReviewRequiredEvent;
import com.fabricmanagement.production.quality.result.domain.event.BatchQualityStandardMissingEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("QualityNotificationListener")
class QualityNotificationListenerTest {

  private static final UUID TENANT = UUID.randomUUID();
  private static final UUID HOLDER = UUID.randomUUID();

  @Mock private NotificationHubService hub;
  @Mock private PermissionRecipientPort permissionRecipients;
  @Mock private ProcessedEventRepository processedEvents;

  private QualityNotificationListener listener;

  @BeforeEach
  void setUp() {
    listener =
        new QualityNotificationListener(
            hub,
            permissionRecipients,
            new IdempotentEventHandler(processedEvents, new SimpleMeterRegistry()));
    when(processedEvents.tryInsert(any(), any())).thenReturn(1);
  }

  @Test
  @DisplayName("a concession goes to whoever may approve quality decisions")
  void concessionGoesToQualityApprovers() {
    when(permissionRecipients.findUsersWithPermission(TENANT, PermissionKey.QUALITY_APPROVE))
        .thenReturn(Set.of(HOLDER));
    var event = new BatchConcessionReviewRequiredEvent(TENANT, UUID.randomUUID(), "B-0042");

    listener.onBatchConcessionReviewRequired(event);

    verify(hub)
        .notifyAll(
            eq(List.of(HOLDER)),
            eq(TENANT),
            eq(NotificationEventType.BATCH_CONCESSION_REVIEW),
            eq(Map.of("batchCode", "B-0042")),
            eq(event.getBatchId()),
            eq("BATCH"));
  }

  @Test
  @DisplayName("a missing standard carries the reason code for the wording to translate")
  void missingStandardCarriesTheReason() {
    when(permissionRecipients.findUsersWithPermission(TENANT, PermissionKey.QUALITY_MANAGE))
        .thenReturn(Set.of(HOLDER));
    var event =
        new BatchQualityStandardMissingEvent(
            TENANT, UUID.randomUUID(), "B-0042", "CO 60% / PES 40%", "NO_QUALITY_PROFILE");

    listener.onBatchQualityStandardMissing(event);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, String>> payload = ArgumentCaptor.forClass(Map.class);
    verify(hub)
        .notifyAll(
            eq(List.of(HOLDER)),
            eq(TENANT),
            eq(NotificationEventType.BATCH_NO_QUALITY_STANDARD),
            payload.capture(),
            eq(event.getBatchId()),
            eq("BATCH"));
    assertThat(payload.getValue())
        .containsEntry("batchCode", "B-0042")
        .containsEntry("target", "CO 60% / PES 40%")
        .containsEntry("reason", "NO_QUALITY_PROFILE");
  }

  @Test
  @DisplayName("expiring certificates are counted, and nobody to tell means nothing is sent")
  void expiringCertificates() {
    when(permissionRecipients.findUsersWithPermission(TENANT, PermissionKey.QUALITY_MANAGE))
        .thenReturn(Set.of());

    listener.onBatchCertificationsExpiring(
        new BatchCertificationsExpiringEvent(TENANT, 3, LocalDate.of(2026, 12, 12)));

    verify(hub, never()).notifyAll(any(), any(), any(), any(), any(), any());
  }
}
