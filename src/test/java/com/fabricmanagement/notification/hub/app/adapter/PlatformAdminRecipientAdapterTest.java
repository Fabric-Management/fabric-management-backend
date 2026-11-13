package com.fabricmanagement.notification.hub.app.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.user.app.UserQueryService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
@DisplayName("PlatformAdminRecipientAdapter")
class PlatformAdminRecipientAdapterTest {

  @Mock private UserQueryService userQueryService;
  @InjectMocks private PlatformAdminRecipientAdapter adapter;

  private Logger logger;
  private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

  @BeforeEach
  void captureLogs() {
    logger = (Logger) LoggerFactory.getLogger(PlatformAdminRecipientAdapter.class);
    logs.start();
    logger.addAppender(logs);
  }

  @AfterEach
  void releaseLogs() {
    logger.detachAppender(logs);
    logs.stop();
  }

  @Test
  @DisplayName("asks the platform tenant for holders of the PLATFORM_ADMIN role")
  void asksThePlatformTenant() {
    UUID administrator = UUID.randomUUID();
    when(userQueryService.findActiveUserIdsByRoleCodes(
            TenantContext.SYSTEM_TENANT_ID, List.of("PLATFORM_ADMIN")))
        .thenReturn(List.of(administrator));

    assertThat(adapter.findPlatformAdministrators()).containsExactly(administrator);
    assertThat(warnings()).isEmpty();
  }

  @Test
  @DisplayName("nobody holds the role: empty result and a warning that says why")
  void warnsWhenThereIsNoPlatformAdministrator() {
    when(userQueryService.findActiveUserIdsByRoleCodes(
            TenantContext.SYSTEM_TENANT_ID, List.of("PLATFORM_ADMIN")))
        .thenReturn(List.of());

    assertThat(adapter.findPlatformAdministrators()).isEmpty();
    assertThat(warnings())
        .singleElement()
        .satisfies(message -> assertThat(message).contains("PLATFORM_ADMIN", "platform tenant"));
  }

  private List<String> warnings() {
    return logs.list.stream()
        .filter(event -> event.getLevel() == Level.WARN)
        .map(ILoggingEvent::getFormattedMessage)
        .toList();
  }
}
