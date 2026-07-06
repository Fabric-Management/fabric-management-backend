package com.fabricmanagement.flowboard.generator.app.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fabricmanagement.sales.quote.domain.event.QuoteSendRequestedEvent;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QuoteSendRequestedEventAdapterTest {

  private final QuoteSendRequestedEventAdapter adapter = new QuoteSendRequestedEventAdapter();

  @Test
  void shouldBuildApprovalTaskContextFromQuoteSendRequest() {
    UUID tenantId = UUID.randomUUID();
    UUID requestId = UUID.randomUUID();
    UUID quoteId = UUID.randomUUID();
    UUID requestedBy = UUID.randomUUID();

    QuoteSendRequestedEvent event =
        new QuoteSendRequestedEvent(tenantId, requestId, quoteId, "Q-2026-001", requestedBy);

    TaskTemplateContext context = adapter.buildContext(event);

    assertEquals(QuoteSendRequestedEvent.class, adapter.getSupportedEventType());
    assertEquals("QuoteSendRequested", adapter.getEventTypeName());
    assertEquals(tenantId, context.tenantId());
    assertEquals(requestId, context.entityId());
    assertEquals("QUOTE_SEND_REQUEST", context.entityType());
    assertEquals("Q-2026-001", context.entityRef());
    assertNull(context.deadline());
    assertEquals("Q-2026-001", context.templateVariables().get("quote.quoteNumber"));
  }
}
