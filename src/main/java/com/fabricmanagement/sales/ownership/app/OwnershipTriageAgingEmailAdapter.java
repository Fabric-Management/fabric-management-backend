package com.fabricmanagement.sales.ownership.app;

import com.fabricmanagement.platform.communication.api.facade.SystemEmailFacade;
import com.fabricmanagement.sales.ownership.domain.OwnershipTriageCase;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

@Component
@RequiredArgsConstructor
public class OwnershipTriageAgingEmailAdapter {

  private final SystemEmailFacade systemEmailFacade;

  @Value("${application.sales.triage.ops-email:}")
  private String opsEmail;

  public void queue(UUID tenantId, OwnershipTriageCase triageCase) {
    if (opsEmail == null || opsEmail.isBlank()) {
      throw new IllegalStateException(
          "application.sales.triage.ops-email is required before ownership aging alerts can run");
    }

    String customerName = HtmlUtils.htmlEscape(triageCase.customerName(), "UTF-8");
    String subject =
        "Sales ownership overdue — "
            + triageCase.customerName().replace('\r', ' ').replace('\n', ' ');
    String html =
        """
        <h2>Sales ownership requires attention</h2>
        <p><strong>Customer:</strong> %s</p>
        <p><strong>Customer ID:</strong> %s</p>
        <p><strong>Gap started:</strong> %s</p>
        <p><strong>Unassigned open quotes:</strong> %d</p>
        """
            .formatted(
                customerName,
                triageCase.customerId(),
                triageCase.gapStartedAt(),
                triageCase.unassignedOpenQuoteCount());
    systemEmailFacade.queueSystemEmail(tenantId, opsEmail.trim(), subject, html);
  }
}
