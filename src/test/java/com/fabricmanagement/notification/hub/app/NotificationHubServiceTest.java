package com.fabricmanagement.notification.hub.app;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fabricmanagement.notification.hub.domain.*;
import com.fabricmanagement.notification.hub.infra.repository.*;
import com.fabricmanagement.notification.i18n.app.TranslationService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(
    strictness = Strictness.LENIENT) // setUp stub'larının bazı testlerde kullanılmaması sorun değil
@DisplayName("NotificationHubService Unit Tests")
class NotificationHubServiceTest {

  @Mock private NotificationTemplateRepository templateRepo;
  @Mock private NotificationQueueRepository queueRepo;
  @Mock private NotificationLogRepository logRepo;
  @Mock private UserNotificationPreferenceRepository prefRepo;
  @Mock private TranslationService translationService;

  @InjectMocks private NotificationHubService notificationHubService;

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final UUID RECIPIENT_ID = UUID.randomUUID();
  private static final String EVENT_TYPE = "WORK_ORDER_PENDING_APPROVAL";

  @Nested
  @DisplayName("notify()")
  class NotifyTests {

    @Test
    @DisplayName("şablon bulunamazsa kuyruk oluşturulmaz")
    void should_skip_when_no_template_found() {
      when(templateRepo.findAllByEventType(EVENT_TYPE)).thenReturn(List.of());

      notificationHubService.notify(
          NotificationContext.of(TENANT_ID, RECIPIENT_ID, EVENT_TYPE, Map.of()));

      verify(queueRepo, never()).save(any());
    }

    @Test
    @DisplayName("CRITICAL importance → tercih yok sayılır, kuyruklanır")
    void should_queue_critical_regardless_of_preference() {
      when(translationService.resolveLocaleForUser(any(), any())).thenReturn("EN");
      var template = mockTemplate(NotificationImportance.CRITICAL, NotificationChannel.IN_APP);
      when(templateRepo.findAllByEventType(EVENT_TYPE)).thenReturn(List.of(template));
      // Kullanıcı IN_APP kapattıysa bile CRITICAL gönderir
      var pref = mock(UserNotificationPreference.class);
      when(pref.isInApp()).thenReturn(false);
      when(prefRepo.findByUserIdAndEventType(RECIPIENT_ID, EVENT_TYPE))
          .thenReturn(Optional.of(pref));

      notificationHubService.notify(
          NotificationContext.of(TENANT_ID, RECIPIENT_ID, EVENT_TYPE, Map.of()));

      verify(queueRepo).save(any(NotificationQueue.class));
    }

    @Test
    @DisplayName("NORMAL importance + kullanıcı IN_APP kapatmış → kuyruklanmaz")
    void should_not_queue_when_user_disabled_in_app() {
      when(translationService.resolveLocaleForUser(any(), any())).thenReturn("EN");
      var template = mockTemplate(NotificationImportance.NORMAL, NotificationChannel.IN_APP);
      when(templateRepo.findAllByEventType(EVENT_TYPE)).thenReturn(List.of(template));

      var pref = mock(UserNotificationPreference.class);
      when(pref.isInApp()).thenReturn(false);
      when(prefRepo.findByUserIdAndEventType(RECIPIENT_ID, EVENT_TYPE))
          .thenReturn(Optional.of(pref));

      notificationHubService.notify(
          NotificationContext.of(TENANT_ID, RECIPIENT_ID, EVENT_TYPE, Map.of()));

      verify(queueRepo, never()).save(any());
    }

    @Test
    @DisplayName("tercih yoksa varsayılan true → kuyruklanır")
    void should_queue_when_no_preference_exists() {
      when(translationService.resolveLocaleForUser(any(), any())).thenReturn("EN");
      var template = mockTemplate(NotificationImportance.NORMAL, NotificationChannel.EMAIL);
      when(templateRepo.findAllByEventType(EVENT_TYPE)).thenReturn(List.of(template));
      when(prefRepo.findByUserIdAndEventType(RECIPIENT_ID, EVENT_TYPE))
          .thenReturn(Optional.empty());

      notificationHubService.notify(
          NotificationContext.of(TENANT_ID, RECIPIENT_ID, EVENT_TYPE, Map.of()));

      ArgumentCaptor<NotificationQueue> captor = ArgumentCaptor.forClass(NotificationQueue.class);
      verify(queueRepo).save(captor.capture());
      assertThat(captor.getValue().getChannel()).isEqualTo(NotificationChannel.EMAIL);
    }

    @Test
    @DisplayName("birden fazla kanal şablonu → her biri ayrı kuyruklanır")
    void should_queue_for_each_channel_template() {
      when(translationService.resolveLocaleForUser(any(), any())).thenReturn("EN");
      var inAppTemplate = mockTemplate(NotificationImportance.HIGH, NotificationChannel.IN_APP);
      var emailTemplate = mockTemplate(NotificationImportance.HIGH, NotificationChannel.EMAIL);
      when(templateRepo.findAllByEventType(EVENT_TYPE))
          .thenReturn(List.of(inAppTemplate, emailTemplate));
      when(prefRepo.findByUserIdAndEventType(any(), any())).thenReturn(Optional.empty());

      notificationHubService.notify(
          NotificationContext.of(TENANT_ID, RECIPIENT_ID, EVENT_TYPE, Map.of()));

      verify(queueRepo, times(2)).save(any(NotificationQueue.class));
    }
  }

  @Nested
  @DisplayName("markRead() / markAllRead()")
  class MarkReadTests {

    @Test
    @DisplayName("markRead — kendi bildirimi okuyor")
    void should_mark_own_notification_as_read() {
      var log = mock(NotificationLog.class);
      when(log.getRecipientId()).thenReturn(RECIPIENT_ID);
      when(logRepo.findById(any())).thenReturn(Optional.of(log));

      notificationHubService.markRead(UUID.randomUUID(), RECIPIENT_ID);

      verify(log).markRead();
    }

    @Test
    @DisplayName("markRead — başkasının bildirimi → okunmaz")
    void should_not_mark_other_users_notification() {
      var log = mock(NotificationLog.class);
      when(log.getRecipientId()).thenReturn(UUID.randomUUID()); // farklı kullanıcı
      when(logRepo.findById(any())).thenReturn(Optional.of(log));

      notificationHubService.markRead(UUID.randomUUID(), RECIPIENT_ID);

      verify(log, never()).markRead();
    }

    @Test
    @DisplayName("markAllRead — repository'ye doğru recipientId gönderilir")
    void should_call_mark_all_read_with_correct_recipient() {
      when(logRepo.markAllReadForRecipient(RECIPIENT_ID)).thenReturn(5);

      int result = notificationHubService.markAllRead(RECIPIENT_ID);

      assertThat(result).isEqualTo(5);
      verify(logRepo).markAllReadForRecipient(RECIPIENT_ID);
    }
  }

  @Nested
  @DisplayName("NotificationQueue domain logic")
  class QueueDomainTests {

    @Test
    @DisplayName("markFailed 3 denemede FAILED yapar")
    void should_become_failed_after_3_retries() {
      var queue =
          NotificationQueue.create(
              TENANT_ID,
              RECIPIENT_ID,
              EVENT_TYPE,
              NotificationChannel.IN_APP,
              NotificationImportance.NORMAL,
              NotificationDeliveryType.INSTANT,
              Map.of(),
              "EN");

      queue.markFailed("error 1"); // retry 1 → PENDING
      assertThat(queue.getStatus()).isEqualTo(NotificationQueueStatus.PENDING);

      queue.markFailed("error 2"); // retry 2 → PENDING
      assertThat(queue.getStatus()).isEqualTo(NotificationQueueStatus.PENDING);

      queue.markFailed("error 3"); // retry 3 → FAILED
      assertThat(queue.getStatus()).isEqualTo(NotificationQueueStatus.FAILED);
      assertThat(queue.getRetryCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("markSent sonrası status SENT ve processedAt set")
    void should_set_sent_state_correctly() {
      var queue =
          NotificationQueue.create(
              TENANT_ID,
              RECIPIENT_ID,
              EVENT_TYPE,
              NotificationChannel.EMAIL,
              NotificationImportance.HIGH,
              NotificationDeliveryType.INSTANT,
              Map.of(),
              "EN");

      queue.markSent();

      assertThat(queue.getStatus()).isEqualTo(NotificationQueueStatus.SENT);
      assertThat(queue.getProcessedAt()).isNotNull();
    }
  }

  // ---- Helpers ----

  private NotificationTemplate mockTemplate(
      NotificationImportance importance, NotificationChannel channel) {
    var template = mock(NotificationTemplate.class);
    when(template.getImportance()).thenReturn(importance);
    when(template.getChannel()).thenReturn(channel);
    when(template.getDeliveryType()).thenReturn(NotificationDeliveryType.INSTANT);
    return template;
  }
}
