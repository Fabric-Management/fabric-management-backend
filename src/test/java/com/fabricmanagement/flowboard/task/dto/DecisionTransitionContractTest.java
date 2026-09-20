package com.fabricmanagement.flowboard.task.dto;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DecisionTransitionContractTest {
  private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

  @Test
  void rejectsUnknownEnvelopeAndPayloadFields() {
    String base =
        """
        {"action":"CONFIRM_PRODUCTION_COVER","expectedVersion":0,"idempotencyKey":"%s",
         "payload":{"caseId":"%s","evidenceId":"%s","evidenceRevision":1,
                    "lineIds":["%s"],"rationale":null,"note":null%s}%s}
        """;
    UUID key = UUID.randomUUID(),
        caseId = UUID.randomUUID(),
        evidence = UUID.randomUUID(),
        line = UUID.randomUUID();
    assertThatThrownBy(
            () ->
                mapper.readValue(
                    base.formatted(key, caseId, evidence, line, ",\"quantity\":\"1\"", ""),
                    DecisionTransitionRequest.class))
        .hasRootCauseInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                mapper.readValue(
                    base.formatted(
                        key,
                        caseId,
                        evidence,
                        line,
                        "",
                        ",\"payloadFingerprint\":\"client-value\""),
                    DecisionTransitionRequest.class))
        .hasRootCauseInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void missingExpectedVersionCannotBecomeVersionZero() {
    String json =
        """
        {"action":"CONFIRM_PRODUCTION_COVER","idempotencyKey":"%s",
         "payload":{"caseId":"%s","evidenceId":"%s","evidenceRevision":1,
                    "lineIds":["%s"],"rationale":null,"note":null}}
        """
            .formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    assertThatThrownBy(() -> mapper.readValue(json, DecisionTransitionRequest.class))
        .hasRootCauseInstanceOf(NullPointerException.class);
  }
}
