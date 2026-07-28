package com.fabricmanagement.platform.communication.api.facade;

import static org.mockito.Mockito.verify;

import com.fabricmanagement.platform.communication.app.EmailOutboxService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SystemEmailFacadeTest {

  @Mock private EmailOutboxService emailOutboxService;

  @Test
  void delegatesToTheTransactionalOutboxWithoutExposingItsEntity() {
    UUID tenantId = UUID.randomUUID();
    SystemEmailFacade facade = new SystemEmailFacade(emailOutboxService);

    facade.queueSystemEmail(tenantId, "ops@example.com", "Subject", "<p>Body</p>");

    verify(emailOutboxService)
        .queueSystemEmail(tenantId, "ops@example.com", "Subject", "<p>Body</p>");
  }
}
