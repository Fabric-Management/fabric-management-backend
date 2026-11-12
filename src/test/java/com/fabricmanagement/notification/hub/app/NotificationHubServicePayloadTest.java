package com.fabricmanagement.notification.hub.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationHubServicePayloadTest {

  @Test
  void aReferencePassedAsArgumentsReachesThePayloadTheLogReads() {
    UUID customerId = UUID.randomUUID();
    NotificationContext ctx =
        NotificationContext.of(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "CUSTOMER_OWNERSHIP_TRIAGE_AGED",
            Map.of("customerName", "Acme"),
            customerId,
            "CUSTOMER");

    assertThat(NotificationHubService.payloadWithReference(ctx))
        .containsEntry("customerName", "Acme")
        .containsEntry("referenceId", customerId.toString())
        .containsEntry("referenceType", "CUSTOMER");
  }

  @Test
  void anExplicitPayloadReferenceWins() {
    NotificationContext ctx =
        NotificationContext.of(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "PO_DELIVERY_LATE",
            Map.of("referenceId", "explicit", "referenceType", "PURCHASE_ORDER"),
            UUID.randomUUID(),
            "OTHER");

    assertThat(NotificationHubService.payloadWithReference(ctx))
        .containsEntry("referenceId", "explicit")
        .containsEntry("referenceType", "PURCHASE_ORDER");
  }

  @Test
  void withoutAReferenceThePayloadIsUnchanged() {
    Map<String, String> payload = Map.of("k", "v");
    NotificationContext ctx =
        NotificationContext.of(UUID.randomUUID(), UUID.randomUUID(), "TASK_BLOCKED", payload);

    assertThat(NotificationHubService.payloadWithReference(ctx)).isSameAs(payload);
  }
}
