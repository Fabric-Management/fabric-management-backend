package com.fabricmanagement.platform.communication.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.fabricmanagement.common.infrastructure.events.FollowUpFeedbackReport;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StuckEventFeedbackEmailAdapterTest {

  private static final String OPS_EMAIL = "fixed-ops@example.test";

  @Mock private EmailOutboxService emailOutboxService;

  private StuckEventFeedbackEmailAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new StuckEventFeedbackEmailAdapter(emailOutboxService, OPS_EMAIL);
  }

  @Test
  void sendsCompleteReportToConfiguredOpsAddress() {
    FollowUpFeedbackReport report = report("SQ-1042", "Order creation did not complete.");

    adapter.sendOpsReport(report);

    ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> htmlCaptor = ArgumentCaptor.forClass(String.class);
    verify(emailOutboxService)
        .queueSystemEmail(
            eq(report.tenantId()), eq(OPS_EMAIL), subjectCaptor.capture(), htmlCaptor.capture());
    assertThat(subjectCaptor.getValue()).contains("SQ-1042");
    assertThat(htmlCaptor.getValue())
        .contains(report.eventType(), report.publicationId().toString(), "SQ-1042");
  }

  @Test
  void escapesDynamicHtmlAndStripsSubjectHeaderBreaks() {
    String entityRef = "SQ-1\r\nBcc: visitor@example.test <a href=\"https://bad.test\">&";
    String summary = "<script>alert(\"x\")</script> & broken";
    FollowUpFeedbackReport report = report(entityRef, summary);

    adapter.sendOpsReport(report);

    ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> htmlCaptor = ArgumentCaptor.forClass(String.class);
    verify(emailOutboxService)
        .queueSystemEmail(
            eq(report.tenantId()), eq(OPS_EMAIL), subjectCaptor.capture(), htmlCaptor.capture());
    assertThat(subjectCaptor.getValue()).doesNotContain("\r", "\n");
    assertThat(htmlCaptor.getValue())
        .doesNotContain("<a href=", "<script>")
        .contains(
            "&lt;a href=&quot;https://bad.test&quot;&gt;&amp;",
            "&lt;script&gt;alert(&quot;x&quot;)&lt;/script&gt; &amp; broken");
  }

  private FollowUpFeedbackReport report(String entityRef, String summary) {
    return new FollowUpFeedbackReport(
        UUID.randomUUID(),
        UUID.randomUUID(),
        "SUPPLIER_QUOTE_ACCEPTED",
        "SUPPLIER_QUOTE",
        entityRef,
        summary,
        "SUPPLIER_QUOTE",
        UUID.randomUUID(),
        UUID.randomUUID(),
        Instant.now().minusSeconds(900));
  }
}
