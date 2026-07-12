package com.fabricmanagement.platform.communication.app;

import com.fabricmanagement.common.infrastructure.events.FollowUpFeedbackReport;
import com.fabricmanagement.common.infrastructure.events.StuckEventFeedbackSender;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

@Component
public class StuckEventFeedbackEmailAdapter implements StuckEventFeedbackSender {

  private final EmailOutboxService emailOutboxService;
  private final String opsEmail;

  public StuckEventFeedbackEmailAdapter(
      EmailOutboxService emailOutboxService,
      @Value("${application.event-visibility.ops-email:ops@fabric-management.local}")
          String opsEmail) {
    this.emailOutboxService = emailOutboxService;
    this.opsEmail = opsEmail;
  }

  @Override
  public void sendOpsReport(FollowUpFeedbackReport report) {
    String subjectReference = stripHeaderBreaks(value(report.entityRef()));
    if (subjectReference.isBlank()) {
      subjectReference = "unknown reference";
    }
    String subject = "Stuck follow-up report: " + subjectReference;
    String html =
        """
        <html><body>
        <h1>Stuck follow-up report</h1>
        <dl>
          <dt>Tenant</dt><dd>%s</dd>
          <dt>Event type</dt><dd>%s</dd>
          <dt>Publication ID</dt><dd>%s</dd>
          <dt>Entity</dt><dd>%s — %s</dd>
          <dt>Summary</dt><dd>%s</dd>
          <dt>Reference</dt><dd>%s / %s</dd>
          <dt>Affected user</dt><dd>%s</dd>
          <dt>Detected at</dt><dd>%s</dd>
          <dt>Age (minutes)</dt><dd>%s</dd>
        </dl>
        </body></html>
        """
            .formatted(
                escape(report.tenantId()),
                escape(report.eventType()),
                escape(report.publicationId()),
                escape(report.entityType()),
                escape(report.entityRef()),
                escape(report.summary()),
                escape(report.referenceType()),
                escape(report.referenceId()),
                escape(report.affectedUserId()),
                escape(report.detectedAt()),
                escape(ageMinutes(report.detectedAt())));

    emailOutboxService.queueSystemEmail(report.tenantId(), opsEmail, subject, html);
  }

  private long ageMinutes(Instant detectedAt) {
    if (detectedAt == null) {
      return 0;
    }
    return Math.max(0, Duration.between(detectedAt, Instant.now()).toMinutes());
  }

  private String escape(Object value) {
    return HtmlUtils.htmlEscape(value(value));
  }

  private String value(Object value) {
    return value == null ? "" : value.toString();
  }

  private String stripHeaderBreaks(String value) {
    return value.replace("\r", "").replace("\n", "");
  }
}
