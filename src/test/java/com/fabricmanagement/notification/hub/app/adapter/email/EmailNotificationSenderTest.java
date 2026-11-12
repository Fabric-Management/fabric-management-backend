package com.fabricmanagement.notification.hub.app.adapter.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fabricmanagement.platform.communication.app.EmailOutboxService;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("E-mail channel of the notification hub")
class EmailNotificationSenderTest {

  @Mock private EmailOutboxService outbox;
  @InjectMocks private EmailNotificationSender sender;

  @Test
  @DisplayName("escapes the plain-text body when it wraps it in HTML; the subject stays plain")
  void escapesTheBody() {
    UUID tenant = UUID.randomUUID();

    sender.send(tenant, "a@example.com", "Smith & Sons", "Smith & Sons <Ltd>\nLine two");

    ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
    verify(outbox).queueEmail(eq(tenant), eq("a@example.com"), eq("Smith & Sons"), html.capture());
    assertThat(html.getValue())
        .contains("Smith &amp; Sons &lt;Ltd&gt;<br>Line two")
        .doesNotContain("<Ltd>");
  }

  @Test
  @DisplayName("skips a blank address")
  void skipsBlankAddress() {
    sender.send(UUID.randomUUID(), " ", "t", "b");

    verify(outbox, never()).queueEmail(any(), anyString(), anyString(), anyString());
  }
}
