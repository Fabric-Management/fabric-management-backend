package com.fabricmanagement.common.infrastructure.events;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.procurement.quote.domain.event.SupplierQuoteAcceptedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class DomainEventSerializationTest {

  private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void supplierQuoteAcceptedEventRoundTripPreservesDomainEventEnvelope() throws Exception {
    MDC.put("traceId", "trace-round-trip");
    SupplierQuoteAcceptedEvent original =
        new SupplierQuoteAcceptedEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    MDC.clear();

    String json = objectMapper.writeValueAsString(original);
    SupplierQuoteAcceptedEvent restored =
        objectMapper.readValue(json, SupplierQuoteAcceptedEvent.class);

    assertThat(restored.getEventId()).isEqualTo(original.getEventId());
    assertThat(restored.getOccurredAt()).isEqualTo(original.getOccurredAt());
    assertThat(restored.getCorrelationId()).isEqualTo(original.getCorrelationId());
    assertThat(restored.getTenantId()).isEqualTo(original.getTenantId());
    assertThat(restored.getQuoteId()).isEqualTo(original.getQuoteId());
    assertThat(restored.getRfqId()).isEqualTo(original.getRfqId());
  }
}
