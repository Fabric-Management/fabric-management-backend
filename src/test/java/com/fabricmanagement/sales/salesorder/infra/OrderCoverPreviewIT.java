package com.fabricmanagement.sales.salesorder.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabricmanagement.inventory.reservation.app.StockReservationService;
import com.fabricmanagement.product.core.domain.Product;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.production.core.batch.domain.Batch;
import com.fabricmanagement.production.core.batch.domain.CreateBatchCommand;
import com.fabricmanagement.production.core.batch.infra.repository.BatchRepository;
import com.fabricmanagement.production.core.workorder.app.WorkOrderService;
import com.fabricmanagement.production.core.workorder.dto.CreateWorkOrderRequest;
import com.fabricmanagement.sales.salesorder.domain.OrderCoverEvidence;
import com.fabricmanagement.sales.salesorder.domain.port.OrderCoverEvidencePort;
import com.fabricmanagement.sales.salesorder.dto.OrderCoverSelectionPreviewRequest;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderCoverEvidenceRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

class OrderCoverPreviewIT extends OrderCoverIntegrationSupport {
  @Autowired StockReservationService reservations;
  @Autowired BatchRepository batches;
  @Autowired WorkOrderService workOrders;
  @MockitoSpyBean OrderCoverEvidenceRepository evidenceRows;

  @Test
  void acceptedSelectionReturnsPerLineFullOpenQuantitiesAndRationaleFlag() throws Exception {
    Cover cover = governed(2);
    var snapshot = refresh(cover);

    performAuthenticated(get("/api/v1/sales-orders/{orderId}/cover", cover.orderId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.lines[0].selectable").value(true))
        .andExpect(jsonPath("$.data.lines[0].rationaleRequiredIfSelected").value(true));

    preview(
            cover,
            new OrderCoverSelectionPreviewRequest(
                snapshot.id(), snapshot.revision(), cover.lineIds()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accepted").value(true))
        .andExpect(jsonPath("$.data.lines.length()").value(2))
        .andExpect(jsonPath("$.data.lines[0].productionQuantity.value").value("10"))
        .andExpect(jsonPath("$.data.rationaleRequired").value(true))
        .andExpect(jsonPath("$.data.total").doesNotExist());
  }

  @Test
  void knownShortfallPreviewsAndConfirmsTheFullOpenQuantity() throws Exception {
    Cover cover = governed(1);
    knownEvidence(cover, "80", "20");
    var snapshot = refresh(cover);

    performAuthenticated(get("/api/v1/sales-orders/{orderId}/cover", cover.orderId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.lines[0].selectable").value(true))
        .andExpect(jsonPath("$.data.lines[0].productionQuantity.value").value("80"))
        .andExpect(jsonPath("$.data.lines[0].rationaleRequiredIfSelected").value(false));
    preview(
            cover,
            new OrderCoverSelectionPreviewRequest(
                snapshot.id(), snapshot.revision(), cover.lineIds()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accepted").value(true))
        .andExpect(jsonPath("$.data.lines[0].productionQuantity.value").value("80"))
        .andExpect(jsonPath("$.data.rationaleRequired").value(false));

    var result = settle(cover, request(cover, snapshot, cover.lineIds(), UUID.randomUUID(), null));
    assertThat(result.result().lines().getFirst().quantity().value()).isEqualTo("80");
    assertThat(
            jdbc.queryForObject(
                "select planned_qty from production.prod_work_order where sales_order_line_id=?",
                BigDecimal.class,
                cover.lineIds().getFirst()))
        .isEqualByComparingTo("80");
  }

  @Test
  void zeroShortfallRemainsSelectableButRequiresRationaleAcrossAllPaths() throws Exception {
    Cover cover = governed(1);
    knownEvidence(cover, "80", "100");
    var snapshot = refresh(cover);

    performAuthenticated(get("/api/v1/sales-orders/{orderId}/cover", cover.orderId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.lines[0].selectable").value(true))
        .andExpect(jsonPath("$.data.lines[0].rationaleRequiredIfSelected").value(true));
    preview(
            cover,
            new OrderCoverSelectionPreviewRequest(
                snapshot.id(), snapshot.revision(), cover.lineIds()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accepted").value(true))
        .andExpect(jsonPath("$.data.rationaleRequired").value(true));
    postTransition(cover, request(cover, snapshot, cover.lineIds(), UUID.randomUUID(), null))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("COVER_PRECONDITION_FAILED"));
    var accepted =
        settle(
            cover,
            request(
                cover,
                snapshot,
                cover.lineIds(),
                UUID.randomUUID(),
                "Produce despite available stock"));
    assertThat(accepted.result().lines().getFirst().quantity().value()).isEqualTo("80");
  }

  @Test
  void mixedKnownAndUnknownShortfallsAggregateTheRationaleRequirement() throws Exception {
    Cover cover = governed(2);
    knownEvidence(cover, "10", "5");
    var snapshot = refresh(cover);

    performAuthenticated(get("/api/v1/sales-orders/{orderId}/cover", cover.orderId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.lines[0].rationaleRequiredIfSelected").value(false))
        .andExpect(jsonPath("$.data.lines[1].rationaleRequiredIfSelected").value(true));
    preview(
            cover,
            new OrderCoverSelectionPreviewRequest(
                snapshot.id(), snapshot.revision(), cover.lineIds()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accepted").value(true))
        .andExpect(jsonPath("$.data.rationaleRequired").value(true));
    postTransition(cover, request(cover, snapshot, cover.lineIds(), UUID.randomUUID(), null))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("COVER_PRECONDITION_FAILED"));
  }

  @Test
  void competingAllocationCarriesTheCurrentLineNumberAndLabel() throws Exception {
    Cover cover = governed(2);
    UUID productId =
        tx(
            () ->
                products
                    .saveAndFlush(
                        com.fabricmanagement.product.core.domain.Product.create(
                            com.fabricmanagement.product.core.domain.ProductType.FABRIC, "kg"))
                    .getId());
    tx(
        () -> {
          for (UUID lineId : cover.lineIds()) {
            var line = lines.findById(lineId).orElseThrow();
            line.setProductId(productId);
            line.setRequestedQty(new BigDecimal("80"));
            lines.save(line);
          }
          lines.flush();
          return null;
        });
    var inputs =
        new OrderCoverEvidencePort.Inputs(
            cover.lineIds().stream()
                .map(id -> new OrderCoverEvidencePort.Demand(id, new BigDecimal("80"), "kg", null))
                .toList(),
            List.of(
                new OrderCoverEvidencePort.Lot(
                    UUID.randomUUID(),
                    productId,
                    "kg",
                    new BigDecimal("100"),
                    new BigDecimal("100"),
                    OrderCoverEvidencePort.Eligibility.ELIGIBLE,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    "shared-lot-" + suffix)));
    stubEvidenceInputs(inputs);
    refresh(cover);

    performAuthenticated(get("/api/v1/sales-orders/{orderId}/cover", cover.orderId()))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.data.evidence.lines[1].competingAllocations[0].lineId")
                .value(cover.lineIds().getFirst().toString()))
        .andExpect(jsonPath("$.data.evidence.lines[1].competingAllocations[0].lineNumber").value(1))
        .andExpect(
            jsonPath("$.data.evidence.lines[1].competingAllocations[0].label")
                .value("Customer specified textile 0"));
  }

  @Test
  void mixedSelectionReportsTheFirstStructuralLineBlock() throws Exception {
    Cover cover = governed(2);
    tx(
        () -> {
          var line = lines.findById(cover.lineIds().getLast()).orElseThrow();
          attachProfile(line, line.getRequirementProfileId(), 2, false);
          return null;
        });
    var snapshot = refresh(cover);

    preview(
            cover,
            new OrderCoverSelectionPreviewRequest(
                snapshot.id(), snapshot.revision(), cover.lineIds()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accepted").value(false))
        .andExpect(jsonPath("$.data.rejection.lineId").value(cover.lineIds().getLast().toString()))
        .andExpect(jsonPath("$.data.rejection.code").value("REQUIREMENT_INCOMPLETE"))
        .andExpect(jsonPath("$.data.rejection.incompleteReasons[0]").value("UNSPECIFIED:WIDTH"));
  }

  @Test
  void staleFingerprintAndUnknownEvidenceFailBeforeAnyLineAssessment() throws Exception {
    Cover cover = governed(1);
    var snapshot = refresh(cover);
    tx(
        () -> {
          var line = lines.findById(cover.lineIds().getFirst()).orElseThrow();
          attachProfile(line, line.getRequirementProfileId(), 2, true);
          return null;
        });

    preview(
            cover,
            new OrderCoverSelectionPreviewRequest(
                snapshot.id(), snapshot.revision(), cover.lineIds()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EVIDENCE_CHANGED"));
    preview(cover, new OrderCoverSelectionPreviewRequest(UUID.randomUUID(), 1, cover.lineIds()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EVIDENCE_CHANGED"));
  }

  @Test
  void unchangedEvidenceRowButChangedSourceInputsReturnsEvidenceChanged() throws Exception {
    Cover cover = governed(1);
    var snapshot = refresh(cover);
    doReturn(new OrderCoverEvidencePort.Inputs(List.of(), List.of()))
        .when(
            org.springframework.test.util.AopTestUtils
                .<OrderCoverEvidencePort>getUltimateTargetObject(evidenceSource))
        .inspect(any(OrderCoverEvidencePort.Requirements.class));

    preview(
            cover,
            new OrderCoverSelectionPreviewRequest(
                snapshot.id(), snapshot.revision(), cover.lineIds()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EVIDENCE_CHANGED"));
  }

  @Test
  void supersededEvidenceRevisionIsRejectedBeforeTheSelectedLinesAreRead() throws Exception {
    Cover cover = governed(1);
    var before = refresh(cover);
    evidence.rebuild(cover.orderId(), cover.caseId());

    preview(
            cover,
            new OrderCoverSelectionPreviewRequest(before.id(), before.revision(), cover.lineIds()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EVIDENCE_CHANGED"));
  }

  @Test
  void foreignLineIsAPlannerRejectionAndNotMalformedInput() throws Exception {
    Cover cover = governed(1);
    Cover other = governed(1);
    var snapshot = refresh(cover);

    preview(
            cover,
            new OrderCoverSelectionPreviewRequest(
                snapshot.id(), snapshot.revision(), List.of(other.lineIds().getFirst())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accepted").value(false))
        .andExpect(jsonPath("$.data.rejection.code").value("LINE_NOT_OPEN"));
    UUID key = UUID.randomUUID();
    postTransition(
            cover,
            request(
                cover,
                snapshot,
                List.of(other.lineIds().getFirst()),
                key,
                "Attempt a foreign line"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("COVER_PRECONDITION_FAILED"));
    assertThat(
            jdbc.queryForObject(
                "select rejection_message from flowboard.task_transition_attempt where"
                    + " tenant_id=? and idempotency_key=?",
                String.class,
                tenant,
                key.toString()))
        .isEqualTo("LINE_NOT_OPEN");
  }

  @Test
  void validCurrentEvidenceMissingTheSelectedCaseLineIsLineNotOpen() throws Exception {
    Cover cover = governed(1);
    var snapshot = refresh(cover);
    OrderCoverEvidence withoutLine = mock(OrderCoverEvidence.class);
    when(withoutLine.getId()).thenReturn(snapshot.id());
    when(withoutLine.getCaseId()).thenReturn(cover.caseId());
    when(withoutLine.getRevision()).thenReturn(snapshot.revision());
    when(withoutLine.getInputFingerprint()).thenReturn(snapshot.inputFingerprint());
    when(withoutLine.getLines()).thenReturn(List.of());
    when(withoutLine.toDto())
        .thenReturn(
            new com.fabricmanagement.sales.salesorder.dto.OrderCoverEvidenceDto(
                snapshot.id(),
                snapshot.caseId(),
                snapshot.revision(),
                snapshot.orderVersion(),
                snapshot.computedAt(),
                snapshot.inputFingerprint(),
                snapshot.ruleVersion(),
                List.of()));
    doReturn(java.util.Optional.of(withoutLine))
        .when(evidenceRows)
        .findByTenantIdAndSalesOrderIdAndId(tenant, cover.orderId(), snapshot.id());
    doReturn(java.util.Optional.of(withoutLine))
        .when(evidenceRows)
        .findFirstByTenantIdAndCaseIdOrderByRevisionDesc(tenant, cover.caseId());

    performAuthenticated(get("/api/v1/sales-orders/{orderId}/cover", cover.orderId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.lines[0].blockReason.code").value("LINE_NOT_OPEN"));
    preview(
            cover,
            new OrderCoverSelectionPreviewRequest(
                snapshot.id(), snapshot.revision(), cover.lineIds()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.rejection.code").value("LINE_NOT_OPEN"));
    UUID key = UUID.randomUUID();
    postTransition(cover, request(cover, snapshot, cover.lineIds(), key, "Missing evidence line"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("COVER_PRECONDITION_FAILED"));
    assertThat(
            jdbc.queryForObject(
                "select rejection_message from flowboard.task_transition_attempt where"
                    + " tenant_id=? and idempotency_key=?",
                String.class,
                tenant,
                key.toString()))
        .isEqualTo("LINE_NOT_OPEN");
  }

  @Test
  void aResolvedCaseLineIsLineNotOpenAgainstCurrentEvidence() throws Exception {
    Cover cover = governed(2);
    var firstEvidence = refresh(cover);
    settle(
        cover,
        request(
            cover,
            firstEvidence,
            List.of(cover.lineIds().getFirst()),
            UUID.randomUUID(),
            "Produce first line"));
    var current = refresh(cover);

    preview(
            cover,
            new OrderCoverSelectionPreviewRequest(
                current.id(), current.revision(), List.of(cover.lineIds().getFirst())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.rejection.code").value("LINE_NOT_OPEN"));
    postTransition(
            cover,
            request(
                cover,
                current,
                List.of(cover.lineIds().getFirst()),
                UUID.randomUUID(),
                "Attempt an already resolved line"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("COVER_PRECONDITION_FAILED"));
  }

  @Test
  void caseWithoutEvidenceRejectsPreviewAndConfirmBeforeLineAssessment() throws Exception {
    Cover cover = governed(1);
    UUID unknownEvidence = UUID.randomUUID();

    performAuthenticated(get("/api/v1/sales-orders/{orderId}/cover", cover.orderId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.lines[0].blockReason.code").value("NO_EVIDENCE"))
        .andExpect(jsonPath("$.data.lines[0].evidenceId").value(org.hamcrest.Matchers.nullValue()));
    preview(cover, new OrderCoverSelectionPreviewRequest(unknownEvidence, 1, cover.lineIds()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EVIDENCE_CHANGED"));
    var payload =
        new com.fabricmanagement.sales.salesorder.dto.OrderCoverEvidenceDto(
            unknownEvidence,
            cover.caseId(),
            1,
            0,
            java.time.Instant.EPOCH,
            "missing",
            "missing",
            List.of());
    postTransition(
            cover, request(cover, payload, cover.lineIds(), UUID.randomUUID(), "Missing evidence"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EVIDENCE_CHANGED"));
  }

  @Test
  void unknownRequirementCompletenessUsesTheSameStructuralCodeAcrossAllPaths() throws Exception {
    Cover cover = governed(1);
    jdbc.update(
        "update sales_ord.sales_order_line set requirement_profile_id=null,"
            + " requirement_profile_version=null, requirement_profile_fingerprint=null,"
            + " requirement_profile_snapshot=null where id=?",
        cover.lineIds().getFirst());
    var snapshot = refresh(cover);

    performAuthenticated(get("/api/v1/sales-orders/{orderId}/cover", cover.orderId()))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.data.lines[0].blockReason.code").value("REQUIREMENT_COMPLETENESS_UNKNOWN"));
    preview(
            cover,
            new OrderCoverSelectionPreviewRequest(
                snapshot.id(), snapshot.revision(), cover.lineIds()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.rejection.code").value("REQUIREMENT_COMPLETENESS_UNKNOWN"));
    UUID key = UUID.randomUUID();
    postTransition(cover, request(cover, snapshot, cover.lineIds(), key, "Requirement not known"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("COVER_PRECONDITION_FAILED"));
    assertThat(
            jdbc.queryForObject(
                "select rejection_message from flowboard.task_transition_attempt where"
                    + " tenant_id=? and idempotency_key=?",
                String.class,
                tenant,
                key.toString()))
        .isEqualTo("REQUIREMENT_COMPLETENESS_UNKNOWN");
  }

  @Test
  void malformedSelectionsAreTypedBadRequests() throws Exception {
    Cover cover = governed(1);
    var snapshot = refresh(cover);
    List<UUID> tooMany =
        IntStream.rangeClosed(0, 100).mapToObj(value -> UUID.randomUUID()).toList();

    preview(
            cover,
            java.util.Map.of(
                "evidenceId", snapshot.id(),
                "evidenceRevision", snapshot.revision(),
                "lineIds", tooMany))
        .andExpect(status().isUnprocessableEntity());
    preview(
            cover,
            java.util.Map.of(
                "evidenceId", snapshot.id(),
                "evidenceRevision", snapshot.revision(),
                "lineIds", List.of(cover.lineIds().getFirst(), cover.lineIds().getFirst())))
        // Uniqueness is enforced while the body is constructed (as in the confirm payload), so a
        // duplicate is an unreadable body (400), not a bean-validation failure (422).
        .andExpect(status().isBadRequest());
    preview(cover, java.util.Map.of("lineIds", cover.lineIds()))
        .andExpect(status().isUnprocessableEntity());
    performAuthenticated(
            post("/api/v1/sales-orders/{orderId}/cover/preview", cover.orderId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"evidenceId\":\""
                        + snapshot.id()
                        + "\",\"evidenceRevision\":1,\"lineIds\":[\"not-a-uuid\"]}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void readableCallerOutsideThePoolReceivesActionNotAllowed() throws Exception {
    Cover cover = governed(1);
    var snapshot = refresh(cover);
    var viewer =
        user("Preview viewer", "sales:read", "sales:write", "flowboard:read", "flowboard:write");
    authenticate(viewer);

    preview(
            cover,
            new OrderCoverSelectionPreviewRequest(
                snapshot.id(), snapshot.revision(), cover.lineIds()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accepted").value(false))
        .andExpect(jsonPath("$.data.rejection.code").value("ACTION_NOT_ALLOWED"))
        .andExpect(jsonPath("$.data.rejection.caseReasonCode").value("OUTSIDE_ROUTING_POOL"));
  }

  @Test
  void readablePoolMemberWithoutWritePermissionReceivesActionNotAllowed() throws Exception {
    Cover cover = governed(1);
    var snapshot = refresh(cover);
    // Pool membership requires a candidate (flowboard:write + sales:write), so the grant is
    // withdrawn after the pool accepted the user; the capability check evaluates fresh grants.
    var viewer =
        user(
            "Pool member losing write",
            "sales:read",
            "sales:write",
            "flowboard:read",
            "flowboard:write");
    configurePool(viewer.getId());
    revoke(viewer, "sales:write");
    authenticate(viewer);

    preview(
            cover,
            new OrderCoverSelectionPreviewRequest(
                snapshot.id(), snapshot.revision(), cover.lineIds()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accepted").value(false))
        .andExpect(jsonPath("$.data.rejection.code").value("ACTION_NOT_ALLOWED"))
        .andExpect(jsonPath("$.data.rejection.caseReasonCode").value("PERMISSION_DENIED"));
  }

  @Test
  void liveReservationAndProductionAreRejectedByTheSamePlannerRules() throws Exception {
    Cover reserved = governed(1);
    var reservedEvidence = refresh(reserved);
    var stock = reservationStock();
    reservations.createReservation(
        reserved.lineIds().getFirst(),
        stock.locationId(),
        stock.productId(),
        "PREVIEW-RES-" + suffix,
        null,
        BigDecimal.ONE);
    performAuthenticated(get("/api/v1/sales-orders/{orderId}/cover", reserved.orderId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.lines[0].blockReason.code").value("ACTIVE_RESERVATION_EXISTS"));
    preview(
            reserved,
            new OrderCoverSelectionPreviewRequest(
                reservedEvidence.id(), reservedEvidence.revision(), reserved.lineIds()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.rejection.code").value("ACTIVE_RESERVATION_EXISTS"));

    Cover produced = governed(1);
    var producedEvidence = refresh(produced);
    workOrders.createWorkOrder(
        CreateWorkOrderRequest.builder()
            .salesOrderId(produced.orderId())
            .salesOrderLineId(produced.lineIds().getFirst())
            .tradingPartnerId(partner)
            .plannedQty(BigDecimal.ONE)
            .unit("kg")
            .currency("GBP")
            .build());
    performAuthenticated(get("/api/v1/sales-orders/{orderId}/cover", produced.orderId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.lines[0].blockReason.code").value("ACTIVE_PRODUCTION_EXISTS"));
    preview(
            produced,
            new OrderCoverSelectionPreviewRequest(
                producedEvidence.id(), producedEvidence.revision(), produced.lineIds()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.rejection.code").value("ACTIVE_PRODUCTION_EXISTS"));
  }

  private org.springframework.test.web.servlet.ResultActions preview(Cover cover, Object request)
      throws Exception {
    return performAuthenticated(
        post("/api/v1/sales-orders/{orderId}/cover/preview", cover.orderId())
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsBytes(request)));
  }

  /**
   * No stubbing: the evidence is computed by the real adapter, then a real batch of the line's
   * product is added. The evidence row is unchanged, but the source inputs are not, so the lockless
   * preview and the locked confirm must both refuse it before any line is assessed.
   */
  @Test
  void realNewStockForTheLineProductMakesTheEvidenceStaleForPreviewAndConfirm() throws Exception {
    Cover cover = governed(1);
    UUID productId =
        tx(() -> products.saveAndFlush(Product.create(ProductType.FABRIC, "kg")).getId());
    tx(
        () -> {
          var line = lines.findById(cover.lineIds().getFirst()).orElseThrow();
          line.setProductId(productId);
          lines.saveAndFlush(line);
          return null;
        });
    var snapshot = refresh(cover);

    tx(
        () ->
            batches.saveAndFlush(
                Batch.create(
                    new CreateBatchCommand(
                        tenant,
                        productId,
                        ProductType.FABRIC,
                        "OC-STALE-" + suffix,
                        null,
                        new BigDecimal("25"),
                        "KG",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null))));

    preview(
            cover,
            new OrderCoverSelectionPreviewRequest(
                snapshot.id(), snapshot.revision(), cover.lineIds()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EVIDENCE_CHANGED"));

    UUID key = UUID.randomUUID();
    postTransition(cover, request(cover, snapshot, cover.lineIds(), key, "Produce to order"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("EVIDENCE_CHANGED"));
    assertThat(attempts(key)).isZero();
    assertNoSettlement();
  }
}
