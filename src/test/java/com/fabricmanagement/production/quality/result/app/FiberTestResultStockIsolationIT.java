package com.fabricmanagement.production.quality.result.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;

@AutoConfigureMockMvc
class FiberTestResultStockIsolationIT extends FiberQualityHttpIntegrationSupport {

  private UUID batchId;
  private UUID firstBaleId;
  private String technicianToken;
  private String approverToken;

  @BeforeEach
  void createLotAndQualityUsers() {
    technicianToken = token("Ellis", "Ford", "QC_TECHNICIAN", "write");
    approverToken = token("Martha", "Shaw", "QC_OFFICER", "approve");

    String batchCode = "ABS-CO-" + tenantId.toString().substring(0, 8);
    batchId = createLot(batchCode, new BigDecimal("500"));
    firstBaleId = createBale(batchId, batchCode + "-B1", new BigDecimal("250"));
    createBale(batchId, batchCode + "-B2", new BigDecimal("250"));
    TenantContext.clear();
  }

  @Test
  void recordingThenApprovingAResultChangesNoStock() throws Exception {
    assertAwaitingDecision();

    UUID resultId = recordPendingResult(null);
    assertAwaitingDecision();

    approve(resultId);
    assertAwaitingDecision();
  }

  @Test
  void onlyAScopedDecisionChangesStock() throws Exception {
    UUID resultId = recordPendingResult(firstBaleId);
    approve(resultId);
    assertAwaitingDecision();

    recordDecision(
        """
        {"scope":"SELECTED_UNITS","outcome":"RELEASED","stockUnitIds":["%s"]}
        """
            .formatted(firstBaleId));
    assertStock("QUARANTINE", "RELEASED", "PENDING_INSPECTION");
    assertThat(decisionScopes()).containsExactly("SELECTED_UNITS");

    recordDecision(
        """
        {"scope":"FULL_LOT","outcome":"NONCONFORMING","reasonCode":"MEASURE_MISMATCH"}
        """);
    assertStock("QC_REJECTED", "NONCONFORMING", "NONCONFORMING");
    assertThat(decisionScopes()).containsExactly("SELECTED_UNITS", "FULL_LOT");
  }

  @Test
  void aRejectionCanGiveOutOfSpecificationAsItsReason() throws Exception {
    useTenant(tenantId);
    String readerToken = token("Lucy", "Bell", "QC_READER", "read");
    TenantContext.clear();
    mvc.perform(get(QUALITY + "/decision-options").header("Authorization", "Bearer " + readerToken))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath(
                "$.data.options[?(@.outcome == 'NONCONFORMING')].reasons[*].code",
                hasItem("OUT_OF_SPECIFICATION")));

    recordDecision(
        """
        {"scope":"SELECTED_UNITS","outcome":"NONCONFORMING",
         "reasonCode":"OUT_OF_SPECIFICATION","stockUnitIds":["%s"]}
        """
            .formatted(firstBaleId));

    assertThat(
            systemJdbc.queryForMap(
                "SELECT decision_scope, origin, outcome, reason_code, remarks FROM"
                    + " production.quality_decision WHERE tenant_id = ? AND batch_id = ?",
                tenantId,
                batchId))
        .containsAllEntriesOf(
            Map.of(
                "decision_scope", "SELECTED_UNITS",
                "origin", "MANUAL",
                "outcome", "NONCONFORMING",
                "reason_code", "OUT_OF_SPECIFICATION"))
        .containsEntry("remarks", null);
    assertStock("QUARANTINE", "NONCONFORMING", "PENDING_INSPECTION");
  }

  private UUID recordPendingResult(UUID baleId) throws Exception {
    String body =
        """
        {"batchId":"%s","stockUnitId":%s,"testDate":"%s","moisturePercent":5}
        """
            .formatted(batchId, json.writeValueAsString(baleId), Instant.now().minusSeconds(60));
    var response =
        mvc.perform(
                post(TESTS)
                    .header("Authorization", "Bearer " + technicianToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.approvalStatus").value("PENDING"))
            .andReturn()
            .getResponse();
    return UUID.fromString(json.readTree(response.getContentAsString()).at("/data/id").asText());
  }

  private void approve(UUID resultId) throws Exception {
    mvc.perform(
            patch(TESTS + "/{id}/approval", resultId)
                .header("Authorization", "Bearer " + approverToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"approvalStatus\":\"APPROVED\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.approvalStatus").value("APPROVED"));

    // Check the ledger only after the real after-commit listener has completed.
    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                assertThat(
                        systemJdbc.queryForObject(
                            "SELECT count(*) FROM event_publication WHERE listener_id LIKE ?"
                                + " AND serialized_event LIKE ? AND completion_date IS NOT NULL",
                            Long.class,
                            "%BatchQcEventListener.onFiberTestResultApproved%",
                            "%" + batchId + "%"))
                    .isEqualTo(1L));
  }

  private void recordDecision(String body) throws Exception {
    mvc.perform(
            post(QUALITY + "/batches/{batchId}/decisions", batchId)
                .header("Authorization", "Bearer " + approverToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated());
  }

  private void assertAwaitingDecision() {
    assertStock("PENDING_QC", "PENDING_INSPECTION", "PENDING_INSPECTION");
    assertThat(decisionScopes()).isEmpty();
  }

  private void assertStock(String batchStatus, String firstBale, String secondBale) {
    assertThat(
            systemJdbc.queryForObject(
                "SELECT status FROM production.production_execution_batch"
                    + " WHERE tenant_id = ? AND id = ?",
                String.class,
                tenantId,
                batchId))
        .isEqualTo(batchStatus);
    assertThat(
            systemJdbc.queryForList(
                "SELECT quality_disposition FROM production.stock_unit"
                    + " WHERE tenant_id = ? AND batch_id = ? ORDER BY barcode",
                String.class,
                tenantId,
                batchId))
        .containsExactly(firstBale, secondBale);
  }

  private List<String> decisionScopes() {
    return systemJdbc.queryForList(
        "SELECT decision_scope FROM production.quality_decision"
            + " WHERE tenant_id = ? AND batch_id = ? ORDER BY seq",
        String.class,
        tenantId,
        batchId);
  }
}
