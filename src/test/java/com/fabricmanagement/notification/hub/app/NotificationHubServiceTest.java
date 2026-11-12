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
import javax.sql.DataSource;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

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
  @Mock private DataSource dataSource;

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
      when(templateRepo.findAllByTenantIdAndEventTypeAndIsActiveTrue(TENANT_ID, EVENT_TYPE))
          .thenReturn(List.of());

      notificationHubService.notify(
          NotificationContext.of(TENANT_ID, RECIPIENT_ID, EVENT_TYPE, Map.of()));

      verify(queueRepo, never()).save(any());
    }

    @Test
    @DisplayName("şablonlar yalnızca olayın tenant'ından aranır")
    void should_look_up_templates_for_the_events_tenant() {
      when(templateRepo.findAllByTenantIdAndEventTypeAndIsActiveTrue(any(), any()))
          .thenReturn(List.of());

      notificationHubService.notify(
          NotificationContext.of(TENANT_ID, RECIPIENT_ID, EVENT_TYPE, Map.of()));

      verify(templateRepo).findAllByTenantIdAndEventTypeAndIsActiveTrue(TENANT_ID, EVENT_TYPE);
    }

    @Test
    @DisplayName("CRITICAL importance → e-posta kapalı olsa bile kuyruklanır")
    void should_queue_critical_email_regardless_of_preference() {
      when(translationService.resolveLocaleForUser(any(), any())).thenReturn("EN");
      var template =
          mockTemplate(NotificationImportance.CRITICAL, NotificationChannel.EMAIL, false);
      stubTemplates(template);
      stubPreference(emailPreference(false));

      notificationHubService.notify(
          NotificationContext.of(TENANT_ID, RECIPIENT_ID, EVENT_TYPE, Map.of()));

      verify(queueRepo).save(any(NotificationQueue.class));
    }

    @Test
    @DisplayName("uygulama içi bildirim kapatılamaz: tercih inApp=false olsa da kuyruklanır")
    void should_always_queue_in_app() {
      when(translationService.resolveLocaleForUser(any(), any())).thenReturn("EN");
      var template = mockTemplate(NotificationImportance.NORMAL, NotificationChannel.IN_APP, false);
      stubTemplates(template);
      var pref = mock(UserNotificationPreference.class);
      when(pref.isInApp()).thenReturn(false);
      stubPreference(pref);

      notificationHubService.notify(
          NotificationContext.of(TENANT_ID, RECIPIENT_ID, EVENT_TYPE, Map.of()));

      verify(queueRepo).save(any(NotificationQueue.class));
    }

    @Test
    @DisplayName("e-posta: tercih yok + şablon varsayılanı kapalı → kuyruklanmaz")
    void should_not_queue_email_when_default_is_off_and_user_never_chose() {
      when(translationService.resolveLocaleForUser(any(), any())).thenReturn("EN");
      stubTemplates(mockTemplate(NotificationImportance.NORMAL, NotificationChannel.EMAIL, false));
      stubNoPreference();

      notificationHubService.notify(
          NotificationContext.of(TENANT_ID, RECIPIENT_ID, EVENT_TYPE, Map.of()));

      verify(queueRepo, never()).save(any());
    }

    @Test
    @DisplayName("e-posta: tercih yok + şablon varsayılanı açık → kuyruklanır")
    void should_queue_email_when_default_is_on_and_user_never_chose() {
      when(translationService.resolveLocaleForUser(any(), any())).thenReturn("EN");
      stubTemplates(mockTemplate(NotificationImportance.HIGH, NotificationChannel.EMAIL, true));
      stubNoPreference();

      notificationHubService.notify(
          NotificationContext.of(TENANT_ID, RECIPIENT_ID, EVENT_TYPE, Map.of()));

      ArgumentCaptor<NotificationQueue> captor = ArgumentCaptor.forClass(NotificationQueue.class);
      verify(queueRepo).save(captor.capture());
      assertThat(captor.getValue().getChannel()).isEqualTo(NotificationChannel.EMAIL);
    }

    @Test
    @DisplayName("e-posta: kullanıcı açtıysa varsayılan kapalı olsa da kuyruklanır")
    void should_queue_email_when_user_turned_it_on() {
      when(translationService.resolveLocaleForUser(any(), any())).thenReturn("EN");
      stubTemplates(mockTemplate(NotificationImportance.NORMAL, NotificationChannel.EMAIL, false));
      stubPreference(emailPreference(true));

      notificationHubService.notify(
          NotificationContext.of(TENANT_ID, RECIPIENT_ID, EVENT_TYPE, Map.of()));

      verify(queueRepo).save(any(NotificationQueue.class));
    }

    @Test
    @DisplayName("e-posta: kullanıcı kapattıysa varsayılan açık olsa da kuyruklanmaz")
    void should_not_queue_email_when_user_turned_it_off() {
      when(translationService.resolveLocaleForUser(any(), any())).thenReturn("EN");
      stubTemplates(mockTemplate(NotificationImportance.HIGH, NotificationChannel.EMAIL, true));
      stubPreference(emailPreference(false));

      notificationHubService.notify(
          NotificationContext.of(TENANT_ID, RECIPIENT_ID, EVENT_TYPE, Map.of()));

      verify(queueRepo, never()).save(any());
    }

    @Test
    @DisplayName("uygulama içi + e-posta şablonu: varsayılan kapalıysa yalnızca uygulama içi")
    void should_queue_only_in_app_when_email_default_is_off() {
      when(translationService.resolveLocaleForUser(any(), any())).thenReturn("EN");
      stubTemplates(
          mockTemplate(NotificationImportance.HIGH, NotificationChannel.IN_APP, false),
          mockTemplate(NotificationImportance.HIGH, NotificationChannel.EMAIL, false));
      stubNoPreference();

      notificationHubService.notify(
          NotificationContext.of(TENANT_ID, RECIPIENT_ID, EVENT_TYPE, Map.of()));

      ArgumentCaptor<NotificationQueue> captor = ArgumentCaptor.forClass(NotificationQueue.class);
      verify(queueRepo).save(captor.capture());
      assertThat(captor.getValue().getChannel()).isEqualTo(NotificationChannel.IN_APP);
    }

    @Test
    @DisplayName("birden fazla kanal şablonu → varsayılanı açık olan her biri kuyruklanır")
    void should_queue_for_each_enabled_channel_template() {
      when(translationService.resolveLocaleForUser(any(), any())).thenReturn("EN");
      stubTemplates(
          mockTemplate(NotificationImportance.HIGH, NotificationChannel.IN_APP, false),
          mockTemplate(NotificationImportance.HIGH, NotificationChannel.EMAIL, true));
      stubNoPreference();

      notificationHubService.notify(
          NotificationContext.of(TENANT_ID, RECIPIENT_ID, EVENT_TYPE, Map.of()));

      verify(queueRepo, times(2)).save(any(NotificationQueue.class));
    }

    private void stubTemplates(NotificationTemplate... templates) {
      when(templateRepo.findAllByTenantIdAndEventTypeAndIsActiveTrue(TENANT_ID, EVENT_TYPE))
          .thenReturn(List.of(templates));
    }

    private void stubNoPreference() {
      when(prefRepo.findByUserIdAndEventType(RECIPIENT_ID, EVENT_TYPE))
          .thenReturn(Optional.empty());
    }

    private void stubPreference(UserNotificationPreference pref) {
      when(prefRepo.findByUserIdAndEventType(RECIPIENT_ID, EVENT_TYPE))
          .thenReturn(Optional.of(pref));
    }

    private UserNotificationPreference emailPreference(boolean email) {
      var pref = mock(UserNotificationPreference.class);
      when(pref.isEmail()).thenReturn(email);
      return pref;
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
      when(logRepo.markAllReadForRecipientAndChannel(RECIPIENT_ID, NotificationChannel.IN_APP))
          .thenReturn(5);

      int result = notificationHubService.markAllRead(RECIPIENT_ID);

      assertThat(result).isEqualTo(5);
      verify(logRepo).markAllReadForRecipientAndChannel(RECIPIENT_ID, NotificationChannel.IN_APP);
    }
  }

  @Nested
  @DisplayName("list() / countUnread() — the centre shows in-app rows only")
  class CentreReadTests {

    private final Pageable page = PageRequest.of(0, 20);

    @Test
    @DisplayName("liste yalnızca uygulama içi satırları okur")
    void should_list_in_app_rows() {
      when(logRepo.findByRecipientAndChannel(RECIPIENT_ID, NotificationChannel.IN_APP, page))
          .thenReturn(Page.empty());

      notificationHubService.list(RECIPIENT_ID, false, page);

      verify(logRepo).findByRecipientAndChannel(RECIPIENT_ID, NotificationChannel.IN_APP, page);
      verify(logRepo, never()).findUnreadByRecipientAndChannel(any(), any(), any());
    }

    @Test
    @DisplayName("unread=true → yalnızca okunmamış uygulama içi satırlar")
    void should_list_only_unread_in_app_rows() {
      when(logRepo.findUnreadByRecipientAndChannel(RECIPIENT_ID, NotificationChannel.IN_APP, page))
          .thenReturn(Page.empty());

      notificationHubService.list(RECIPIENT_ID, true, page);

      verify(logRepo)
          .findUnreadByRecipientAndChannel(RECIPIENT_ID, NotificationChannel.IN_APP, page);
      verify(logRepo, never()).findByRecipientAndChannel(any(), any(), any());
    }

    @Test
    @DisplayName("okunmamış sayısı e-posta satırlarını saymaz")
    void should_count_in_app_rows_only() {
      when(logRepo.countUnreadByRecipientAndChannel(RECIPIENT_ID, NotificationChannel.IN_APP))
          .thenReturn(3L);

      assertThat(notificationHubService.countUnread(RECIPIENT_ID)).isEqualTo(3L);
      verify(logRepo, never())
          .countUnreadByRecipientAndChannel(RECIPIENT_ID, NotificationChannel.EMAIL);
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
      NotificationImportance importance, NotificationChannel channel, boolean enabledByDefault) {
    var template = mock(NotificationTemplate.class);
    when(template.getImportance()).thenReturn(importance);
    when(template.getChannel()).thenReturn(channel);
    when(template.getDeliveryType()).thenReturn(NotificationDeliveryType.INSTANT);
    when(template.isEnabledByDefault()).thenReturn(enabledByDefault);
    return template;
  }
}
