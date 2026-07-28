package com.fabricmanagement.platform.communication.api.facade;

import com.fabricmanagement.platform.communication.app.EmailOutboxService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Public cross-module API for queueing tenant-owned operational email. */
@Component
@RequiredArgsConstructor
public class SystemEmailFacade {

  private final EmailOutboxService emailOutboxService;

  public void queueSystemEmail(UUID tenantId, String recipient, String subject, String htmlBody) {
    emailOutboxService.queueSystemEmail(tenantId, recipient, subject, htmlBody);
  }
}
