package com.fabricmanagement.flowboard.generator.app.adapter;

import com.fabricmanagement.sales.quote.domain.event.QuoteSendRequestedEvent;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class QuoteSendRequestedEventAdapter implements DomainEventAdapter<QuoteSendRequestedEvent> {

  @Override
  public Class<QuoteSendRequestedEvent> getSupportedEventType() {
    return QuoteSendRequestedEvent.class;
  }

  @Override
  public String getEventTypeName() {
    return "QuoteSendRequested";
  }

  @Override
  public TaskTemplateContext buildContext(QuoteSendRequestedEvent event) {
    return new TaskTemplateContext(
        event.getTenantId(),
        event.getQuoteSendRequestId(),
        "QUOTE_SEND_REQUEST",
        event.getQuoteNumber(),
        null,
        Map.of("quote.quoteNumber", event.getQuoteNumber()));
  }
}
