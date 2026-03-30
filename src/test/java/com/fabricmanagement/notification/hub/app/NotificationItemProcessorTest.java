package com.fabricmanagement.notification.hub.app;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fabricmanagement.notification.hub.domain.*;
import com.fabricmanagement.notification.hub.infra.email.EmailNotificationSender;
import com.fabricmanagement.notification.hub.infra.repository.*;
import com.fabricmanagement.notification.hub.infra.websocket.InAppNotificationSender;
import com.fabricmanagement.notification.i18n.app.TranslationService;
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
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("NotificationItemProcessor Unit Tests")
class NotificationItemProcessorTest {

  @Mock private NotificationQueueRepository queueRepo;
  @Mock private NotificationLogRepository logRepo;
  @Mock private NotificationTemplateRepository templateRepo;
  @Mock private TranslationService translationService;
  @Mock private InAppNotificationSender inAppSender;
  @Mock private EmailNotificationSender emailSender;
  @Mock private NotificationUserQueryService userQueryService;

  @InjectMocks private NotificationItemProcessor processor;

  private static final UUID TENANT_ID = UUID.randomUUID();
  private static final UUID RECIPIENT_ID = UUID.randomUUID();
  private static final String EVENT_TYPE = "BATCH_QC_FAILED";

  private NotificationQueue createQueueItem(NotificationChannel channel) {
    var q =
        NotificationQueue.create(
            TENANT_ID,
            RECIPIENT_ID,
            EVENT_TYPE,
            channel,
            NotificationImportance.CRITICAL,
            NotificationDeliveryType.INSTANT,
            Map.of("batchCode", "B-001"),
            "TR");
    q.setId(UUID.randomUUID());
    return q;
  }

  private void stubLockedQueueRow(NotificationQueue item) {
    when(queueRepo.findByIdWithWriteLock(item.getId())).thenReturn(Optional.of(item));
  }

  @Nested
  @DisplayName("processItem()")
  class ProcessItemTests {

    @Test
    @DisplayName("IN_APP — başarılı gönderim → SENT + log kaydı")
    void should_process_in_app_successfully() {
      var item = createQueueItem(NotificationChannel.IN_APP);
      var template = mockTemplate();

      when(templateRepo.findByEventTypeAndChannel(EVENT_TYPE, NotificationChannel.IN_APP))
          .thenReturn(Optional.of(template));
      when(translationService.translateAndRender(any(), any(), any(), any()))
          .thenReturn("Rendered text");
      stubLockedQueueRow(item);

      processor.processItem(item.getId());

      assertThat(item.getStatus()).isEqualTo(NotificationQueueStatus.SENT);
      assertThat(item.getProcessedAt()).isNotNull();
      verify(inAppSender).send(eq(RECIPIENT_ID), anyMap());
      verify(logRepo).save(any(NotificationLog.class));
      // queueRepo.save çağrılmalı: markProcessing + markSent = 2 kez
      verify(queueRepo, atLeast(2)).save(item);
    }

    @Test
    @DisplayName("EMAIL — kullanıcı email'i varsa gönderilir")
    void should_send_email_when_user_has_email() {
      var item = createQueueItem(NotificationChannel.EMAIL);
      var template = mockTemplate();

      when(templateRepo.findByEventTypeAndChannel(EVENT_TYPE, NotificationChannel.EMAIL))
          .thenReturn(Optional.of(template));
      when(translationService.translateAndRender(any(), any(), any(), any()))
          .thenReturn("Email body");
      when(userQueryService.findEmailByUserId(RECIPIENT_ID))
          .thenReturn(Optional.of("user@test.com"));
      stubLockedQueueRow(item);

      processor.processItem(item.getId());

      verify(emailSender).send("user@test.com", "Email body", "Email body");
      assertThat(item.getStatus()).isEqualTo(NotificationQueueStatus.SENT);
    }

    @Test
    @DisplayName("Template bulunamazsa → FAILED")
    void should_mark_failed_when_no_template() {
      var item = createQueueItem(NotificationChannel.IN_APP);
      when(templateRepo.findByEventTypeAndChannel(anyString(), any())).thenReturn(Optional.empty());
      stubLockedQueueRow(item);

      processor.processItem(item.getId());

      // İlk markFailed: retryCount=1 → hâlâ PENDING
      assertThat(item.getRetryCount()).isEqualTo(1);
      verify(inAppSender, never()).send(any(), anyMap());
      verify(logRepo, never()).save(any(NotificationLog.class));
    }

    @Test
    @DisplayName("Gönderim hatası → markFailed çağrılır")
    void should_mark_failed_on_send_error() {
      var item = createQueueItem(NotificationChannel.IN_APP);
      var template = mockTemplate();

      when(templateRepo.findByEventTypeAndChannel(EVENT_TYPE, NotificationChannel.IN_APP))
          .thenReturn(Optional.of(template));
      when(translationService.translateAndRender(any(), any(), any(), any())).thenReturn("Title");
      doThrow(new RuntimeException("WebSocket down")).when(inAppSender).send(any(), anyMap());
      stubLockedQueueRow(item);

      processor.processItem(item.getId());

      assertThat(item.getRetryCount()).isEqualTo(1);
      assertThat(item.getLastError()).contains("WebSocket down");
    }

    @Test
    @DisplayName("Hata mesajı 500+ karakter ise kısaltılır")
    void should_truncate_long_error_message() {
      var item = createQueueItem(NotificationChannel.IN_APP);
      var template = mockTemplate();

      when(templateRepo.findByEventTypeAndChannel(EVENT_TYPE, NotificationChannel.IN_APP))
          .thenReturn(Optional.of(template));
      when(translationService.translateAndRender(any(), any(), any(), any())).thenReturn("Title");
      String longError = "X".repeat(600);
      doThrow(new RuntimeException(longError)).when(inAppSender).send(any(), anyMap());
      stubLockedQueueRow(item);

      processor.processItem(item.getId());

      assertThat(item.getLastError()).hasSize(503); // 500 + "..."
      assertThat(item.getLastError()).endsWith("...");
    }

    @Test
    @DisplayName("PUSH — token yoksa log + skip, hata fırlatmaz")
    void should_skip_push_when_no_token() {
      var item = createQueueItem(NotificationChannel.PUSH);
      var template = mockTemplate();

      when(templateRepo.findByEventTypeAndChannel(EVENT_TYPE, NotificationChannel.PUSH))
          .thenReturn(Optional.of(template));
      when(translationService.translateAndRender(any(), any(), any(), any()))
          .thenReturn("Push text");
      when(userQueryService.findPushTokenByUserId(RECIPIENT_ID)).thenReturn(Optional.empty());
      stubLockedQueueRow(item);

      processor.processItem(item.getId());

      assertThat(item.getStatus()).isEqualTo(NotificationQueueStatus.SENT);
      verify(logRepo).save(any(NotificationLog.class));
    }
  }

  @Nested
  @DisplayName("NotificationLog oluşturma")
  class LogCreationTests {

    @Test
    @DisplayName("referenceId payload'dan çıkarılır ve log'a yazılır")
    void should_extract_reference_id_from_payload() {
      UUID refId = UUID.randomUUID();
      var item =
          NotificationQueue.create(
              TENANT_ID,
              RECIPIENT_ID,
              EVENT_TYPE,
              NotificationChannel.IN_APP,
              NotificationImportance.CRITICAL,
              NotificationDeliveryType.INSTANT,
              Map.of(
                  "batchCode", "B-001", "referenceId", refId.toString(), "referenceType", "BATCH"),
              "TR");

      var template = mockTemplate();
      when(templateRepo.findByEventTypeAndChannel(EVENT_TYPE, NotificationChannel.IN_APP))
          .thenReturn(Optional.of(template));
      when(translationService.translateAndRender(any(), any(), any(), any())).thenReturn("Text");
      item.setId(UUID.randomUUID());
      stubLockedQueueRow(item);

      processor.processItem(item.getId());

      ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
      verify(logRepo).save(logCaptor.capture());

      assertThat(logCaptor.getValue().getReferenceId()).isEqualTo(refId);
      assertThat(logCaptor.getValue().getReferenceType()).isEqualTo("BATCH");
    }
  }

  private NotificationTemplate mockTemplate() {
    var template = mock(NotificationTemplate.class);
    when(template.getTitleKey()).thenReturn("notification.batch_qc_failed.title");
    when(template.getBodyKey()).thenReturn("notification.batch_qc_failed.body");
    when(template.getImportance()).thenReturn(NotificationImportance.CRITICAL);
    when(template.getChannel()).thenReturn(NotificationChannel.IN_APP);
    when(template.getDeliveryType()).thenReturn(NotificationDeliveryType.INSTANT);
    return template;
  }
}
