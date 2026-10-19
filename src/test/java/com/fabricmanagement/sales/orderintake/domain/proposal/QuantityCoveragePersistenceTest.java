package com.fabricmanagement.sales.orderintake.domain.proposal;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class QuantityCoveragePersistenceTest {
  @Test
  void oldStoredEvaluationKeepsPolicyAndCoverageUnknown() throws Exception {
    var mapper = new ObjectMapper().findAndRegisterModules();
    var result =
        mapper.readValue(
            """
        {"status":"OPTIONS","requestedQuantity":100,"unit":"M","canonicalUnit":"M",
         "options":[],"mainOptionIndex":null,"searchComplete":true,"eligiblePieces":0,
         "unknownPieces":0,"excludedPieces":0,"unknownReasons":[],"agedByProductionDate":false}
        """,
            QuantityEvaluationResult.class);
    assertThat(result.policyCode()).isEqualTo(ProposalPolicyCode.UNKNOWN);
    assertThat(result.effectiveMinHealthyPieces()).isNull();
    assertThat(result.coverage().status()).isEqualTo(QuantityCoverage.Status.UNKNOWN);
    var json = mapper.readTree(mapper.writeValueAsString(result.coverage()));
    assertThat(json.has("coveringOptionKey")).isTrue();
    assertThat(json.get("coveringOptionKey").isNull()).isTrue();
  }
}
