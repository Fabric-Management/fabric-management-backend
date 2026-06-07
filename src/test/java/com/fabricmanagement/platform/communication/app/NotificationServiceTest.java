package com.fabricmanagement.platform.communication.app;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.tenant.TenantQueryPort;
import com.fabricmanagement.common.infrastructure.tenant.TenantReference;
import com.fabricmanagement.platform.communication.domain.strategy.EmailStrategy;
import com.fabricmanagement.platform.tenant.domain.TenantType;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService (Unit Test)")
class NotificationServiceTest {

  @Mock private EmailStrategy emailStrategy;
  @Mock private EmailOutboxService emailOutboxService;
  @Mock private TenantQueryPort tenantQueryPort;

  @InjectMocks private NotificationService notificationService;

  private final UUID TENANT_ID = UUID.randomUUID();

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT_ID);
    ReflectionTestUtils.setField(notificationService, "useOutbox", true);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Nested
  @DisplayName("sendNotification")
  class SendNotification {

    @Test
    @DisplayName("Should skip email sending (async) when tenant is PLAYGROUND")
    void shouldSkipAsyncEmailWhenPlayground() {
      TenantReference playgroundTenant =
          new TenantReference(TENANT_ID, "Playground", "pg-1", TenantType.PLAYGROUND.name());
      when(tenantQueryPort.findById(TENANT_ID)).thenReturn(Optional.of(playgroundTenant));

      notificationService.sendNotification("test@test.com", "Subject", "Message");

      verify(emailOutboxService, never()).queueEmail(anyString(), anyString(), anyString());
      verify(emailStrategy, never()).sendEmail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Should skip email sending (sync) when tenant is PLAYGROUND")
    void shouldSkipSyncEmailWhenPlayground() {
      TenantReference playgroundTenant =
          new TenantReference(TENANT_ID, "Playground", "pg-1", TenantType.PLAYGROUND.name());
      when(tenantQueryPort.findById(TENANT_ID)).thenReturn(Optional.of(playgroundTenant));

      notificationService.sendNotificationSync("test@test.com", "Subject", "Message");

      verify(emailOutboxService, never()).queueEmail(anyString(), anyString(), anyString());
      verify(emailStrategy, never()).sendEmail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Should send email when tenant is REGULAR")
    void shouldSendEmailWhenRegular() {
      TenantReference regularTenant =
          new TenantReference(TENANT_ID, "Regular", "reg-1", TenantType.REGULAR.name());
      when(tenantQueryPort.findById(TENANT_ID)).thenReturn(Optional.of(regularTenant));

      notificationService.sendNotificationSync("test@test.com", "Subject", "Message");

      verify(emailOutboxService).queueEmail(eq("test@test.com"), eq("Subject"), eq("Message"));
    }
  }
}
