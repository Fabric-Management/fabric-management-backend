package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.PieceState;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.ProposalLot;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.ProposalPiece;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.ProposalStock;
import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.WidthEvidence;
import com.fabricmanagement.production.core.batch.domain.PrimaryMeasure;
import com.fabricmanagement.sales.orderintake.app.ProductCorrectionService;
import com.fabricmanagement.sales.orderintake.app.QuantityAcceptanceService;
import com.fabricmanagement.sales.orderintake.domain.AcceptanceChannel;
import com.fabricmanagement.sales.orderintake.domain.QuantityProposal;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityEvaluationResult;
import com.fabricmanagement.sales.orderintake.domain.proposal.QuantityOption;
import com.fabricmanagement.sales.orderintake.dto.FulfilmentDtos;
import com.fabricmanagement.sales.orderintake.dto.OrderIntakeRequests;
import com.fabricmanagement.sales.orderintake.infra.repository.QuantityProposalRepository;
import com.fabricmanagement.sales.salesorder.domain.DeliveryTerm;
import com.fabricmanagement.sales.salesorder.domain.DeliveryTermStatus;
import com.fabricmanagement.sales.salesorder.domain.IncotermsVersion;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditBase;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditOutcome;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditResult;
import com.fabricmanagement.sales.salesorder.dto.UpdateSalesOrderLineRequest;
import com.fabricmanagement.sales.salesorder.dto.UpdateSalesOrderRequest;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * CEDIT-05 L01, L04–L09: what a subscriber sees when the order changes through the real writers,
 * and what it must not see (uncommitted, replayed, unchanged, conflicting or rolled-back work).
 */
class SalesOrderLiveChannelIT extends SalesOrderLiveItSupport {

  @Autowired private QuantityAcceptanceService quantityAcceptances;
  @Autowired private ProductCorrectionService productCorrections;
  @Autowired private QuantityProposalRepository proposals;
  @Autowired private SalesOrderService salesOrders;
  @Autowired private OrderFlowService flows;

  @Test
  @DisplayName(
      "L01: ready first, with the order id and the version as a decimal string; 0 is valid")
  void readyCarriesTheCommittedVersion() {
    jdbc.update("UPDATE sales_ord.sales_order SET version = 0 WHERE id = ?", orderId);
    LiveSse stream = subscribe(actorB);

    LiveSse.Frame first = ready(stream);
    JsonNode body = first.body();
    assertThat(body.path("resourceId").asText()).isEqualTo(orderId.toString());
    assertThat(body.path("revision").isTextual()).isTrue();
    assertThat(body.path("revision").asText()).isEqualTo("0");
    assertThat(UUID.fromString(body.path("connectionId").asText())).isNotNull();
    assertThat(stream.headers().firstValue("Content-Type").orElseThrow())
        .startsWith("text/event-stream");
    assertThat(stream.headers().firstValue("Cache-Control").orElseThrow()).contains("no-store");
    assertThat(stream.headers().firstValue("X-Accel-Buffering")).contains("no");
    assertThat(stream.rawText()).doesNotContain("\nid:").doesNotStartWith("id:");
  }

  @Test
  @DisplayName("L04: one user, two tabs: two live connections; closing one leaves the other")
  void twoTabsOfOneUser() {
    LiveSse first = subscribe(actorA);
    LiveSse second = subscribe(actorA);
    String firstId = ready(first).body().path("connectionId").asText();
    String secondId = ready(second).body().path("connectionId").asText();
    assertThat(firstId).isNotEqualTo(secondId);
    assertThat(liveRegistry.size()).isEqualTo(2);

    first.close();
    // The server notices the closed socket on its next write (a keepalive at the latest).
    awaitCondition(() -> liveRegistry.size() == 1);

    saved(actorA, body(UUID.randomUUID(), open(actorA).baseId(), "notes", set("Second tab")));
    expectInvalidated(second, orderVersion());
  }

  @Test
  @DisplayName("L05: uncommitted work is invisible; header and line-only saves signal after commit")
  void committedSavesSignalUncommittedDoNot() throws Exception {
    LiveSse stream = subscribe(actorB);
    ready(stream);
    long before = orderVersion();

    try (Connection writer = ownerConnection()) {
      writer.setAutoCommit(false);
      try (PreparedStatement bump =
          writer.prepareStatement(
              "UPDATE sales_ord.sales_order SET version = version + 1 WHERE id = ?")) {
        bump.setObject(1, orderId);
        bump.executeUpdate();
      }
      expectQuiet(stream);
      writer.commit();
    }
    expectInvalidated(stream, before + 1);

    SalesOrderEditResult header =
        saved(actorA, body(UUID.randomUUID(), open(actorA).baseId(), "notes", set("Live header")));
    assertThat(header.outcome()).isEqualTo(SalesOrderEditOutcome.APPLIED);
    expectInvalidated(stream, orderVersion());

    SalesOrderEditResult lineOnly =
        saved(
            actorA,
            withLines(
                body(UUID.randomUUID(), open(actorA).baseId()),
                List.of(update(l1, "quantity", set(quantity("1100", "M"))))));
    assertThat(lineOnly.outcome()).isEqualTo(SalesOrderEditOutcome.APPLIED);
    assertThat(lineOnly.resultVersion()).isEqualTo(orderVersion());
    expectInvalidated(stream, orderVersion());
  }

  @Test
  @DisplayName(
      "L06: a replay, a no-change, a conflict and a rollback send nothing; version and history"
          + " stay")
  void nothingCommittedNothingSignalled() throws Exception {
    LiveSse stream = subscribe(actorC);
    ready(stream);
    SalesOrderEditBase baseB = open(actorB);
    SalesOrderEditBase baseC = open(actorC);
    UUID operation = UUID.randomUUID();
    var first = body(operation, open(actorA).baseId(), "notes", set("One"));
    saved(actorA, first);
    expectInvalidated(stream, orderVersion());
    long version = orderVersion();
    int history = historyRows();

    SalesOrderEditResult replay = saved(actorA, first);
    assertThat(replay.replayed()).isTrue();
    expectQuiet(stream);

    SalesOrderEditResult noChange =
        saved(actorB, body(UUID.randomUUID(), baseB.baseId(), "notes", set("One")));
    assertThat(noChange.outcome()).isEqualTo(SalesOrderEditOutcome.NO_CHANGE);
    expectQuiet(stream);

    conflicted(actorC, body(UUID.randomUUID(), baseC.baseId(), "notes", set("Two")));
    expectQuiet(stream);

    try (Connection writer = ownerConnection()) {
      writer.setAutoCommit(false);
      try (PreparedStatement bump =
          writer.prepareStatement(
              "UPDATE sales_ord.sales_order SET version = version + 1 WHERE id = ?")) {
        bump.setObject(1, orderId);
        bump.executeUpdate();
      }
      writer.rollback();
    }
    expectQuiet(stream);

    assertThat(orderVersion()).isEqualTo(version);
    assertThat(historyRows()).isEqualTo(history);
  }

  @Test
  @DisplayName(
      "L07: quantity acceptance, product correction, legacy PUT and leaving the draft signal;"
          + " leaving the draft keeps a reader's stream open")
  void otherWritersSignal() {
    LiveSse stream = subscribe(actorB);
    ready(stream);

    Object accepted = recordAcceptance();
    assertThat(accepted).isNotInstanceOf(RuntimeException.class);
    expectInvalidated(stream, orderVersion());

    UUID p9 = UUID.randomUUID();
    FulfilmentDtos.CorrectProduct correction =
        new FulfilmentDtos.CorrectProduct(
            p1,
            p9,
            List.of(new FulfilmentDtos.LineVersion(l1, lineVersion(l1))),
            "Wrong article chosen");
    Object corrected =
        as(actorA, () -> productCorrections.correct(orderId, correction, actorA.id()));
    assertThat(corrected).isNotInstanceOf(RuntimeException.class);
    expectInvalidated(stream, orderVersion());

    // Built first: reading the lines runs in its own tenant step.
    UpdateSalesOrderRequest legacy = legacyRequest("Legacy");
    Object put = as(actorA, () -> salesOrders.updateOrder(orderId, actorA.id(), legacy));
    assertThat(put).isNotInstanceOf(RuntimeException.class);
    expectInvalidated(stream, orderVersion());

    Object submitted = as(actorA, () -> flows.submit(orderId, actorA.id()));
    assertThat(submitted).isNotInstanceOf(RuntimeException.class);
    expectInvalidated(stream, orderVersion());
    // Out of the draft, still readable: the stream goes on.
    expectQuiet(stream);
    assertThat(stream.ended()).isFalse();
  }

  @Test
  @DisplayName(
      "L08/L09: commits right after opening and in quick succession end at the latest version;"
          + " a revision never goes back")
  void quickCommitsEndAtTheLatestRevision() {
    LiveSse stream = subscribe(actorB);
    for (int i = 0; i < 3; i++) {
      saved(actorA, body(UUID.randomUUID(), open(actorA).baseId(), "notes", set("Quick " + i)));
    }
    long latest = orderVersion();

    List<Long> seen = new ArrayList<>();
    LiveSse.Frame first = ready(stream);
    seen.add(Long.parseLong(first.revision()));
    while (seen.getLast() < latest) {
      LiveSse.Frame next = stream.nextEvent(WAIT);
      assertThat(next.event()).isEqualTo("invalidated");
      seen.add(Long.parseLong(next.revision()));
    }
    assertThat(seen.getLast()).isEqualTo(latest);
    assertThat(seen).isSorted().doesNotHaveDuplicates();
    expectQuiet(stream);
  }

  @Test
  @DisplayName(
      "L21: ready is flushed at once, keepalives arrive, credentialed CORS answers the origin, and"
          + " no frame carries field values, people or the token")
  void flushedFramesWithoutPersonalData() {
    String token = token(actorB);
    long started = System.nanoTime();
    LiveSse stream =
        subscribe(
            port,
            orderId,
            java.util.Map.of(
                "Authorization", "Bearer " + token, "Origin", "http://localhost:3000"));
    ready(stream);
    assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(3));
    assertThat(stream.headers().firstValue("Access-Control-Allow-Origin"))
        .contains("http://localhost:3000");
    assertThat(stream.headers().firstValue("Access-Control-Allow-Credentials")).contains("true");

    saved(actorA, body(UUID.randomUUID(), open(actorA).baseId(), "notes", set("Secret note")));
    expectInvalidated(stream, orderVersion());
    stream.eventsUntilHeartbeats(1, WAIT);

    assertThat(stream.rawText()).contains(": keepalive");
    assertThat(stream.rawText())
        .doesNotContain(token)
        .doesNotContain("Secret note")
        .doesNotContain("Jane Hill")
        .doesNotContain("jane@example.com")
        .doesNotContain("Avery")
        .doesNotContain("Blake")
        .doesNotContain(actorA.id().toString())
        .doesNotContain(tenantId.toString());
  }

  // ── writers ───────────────────────────────────────────────────────────────

  /** The real QuantityAcceptance.record on L1: the customer takes 1020 M of one piece. */
  private Object recordAcceptance() {
    UUID lot = UUID.randomUUID();
    UUID piece = UUID.randomUUID();
    when(proposalStock.find(any()))
        .thenReturn(
            new ProposalStock(
                List.of(
                    new ProposalLot(
                        lot,
                        "LOT-1",
                        null,
                        PrimaryMeasure.LENGTH,
                        "M",
                        WidthEvidence.NOT_REQUIRED,
                        List.of(
                            new ProposalPiece(
                                piece,
                                "R1",
                                new BigDecimal("1020"),
                                PieceState.ELIGIBLE,
                                null,
                                1L)))),
                List.of()));
    when(proposalStock.measureFor(any())).thenReturn(Optional.of(PrimaryMeasure.LENGTH));
    QuantityOption above =
        new QuantityOption(
            "ABOVE:0123456789abcdef",
            QuantityOption.OptionKind.ABOVE,
            QuantityOption.Compatibility.SINGLE_LOT,
            new BigDecimal("1020"),
            new BigDecimal("1020"),
            new BigDecimal("20"),
            new BigDecimal("2"),
            false,
            List.of(
                new QuantityOption.LotPart(
                    lot,
                    "LOT-1",
                    List.of(piece),
                    new BigDecimal("1020"),
                    0,
                    0,
                    false,
                    null,
                    null)));
    Object proposal =
        as(
            actorA,
            () ->
                transactions.execute(
                    status ->
                        proposals
                            .save(
                                QuantityProposal.record(
                                    orderId,
                                    l1,
                                    p1,
                                    new BigDecimal("1000"),
                                    "M",
                                    new QuantityEvaluationResult(
                                        QuantityEvaluationResult.EvaluationStatus.OPTIONS,
                                        new BigDecimal("1000"),
                                        "M",
                                        "M",
                                        List.of(above),
                                        1,
                                        0,
                                        0,
                                        List.of(),
                                        false,
                                        0),
                                    "ab".repeat(32),
                                    actorA.id(),
                                    Instant.now()))
                            .getId()));
    if (proposal instanceof RuntimeException failure) {
      throw failure;
    }
    return as(
        actorA,
        () ->
            quantityAcceptances.record(
                orderId,
                l1,
                new OrderIntakeRequests.RecordQuantityAcceptance(
                    (UUID) proposal,
                    above.optionKey(),
                    false,
                    null,
                    "Buyer by phone",
                    AcceptanceChannel.PHONE,
                    Instant.now(),
                    null,
                    null,
                    true,
                    "record-" + UUID.randomUUID()),
                actorA.id()));
  }

  /** A legacy full replace at the current version: header as fixed, lines exactly as stored. */
  private UpdateSalesOrderRequest legacyRequest(String notes) {
    UpdateSalesOrderRequest request = new UpdateSalesOrderRequest();
    request.setVersion(orderVersion());
    request.setOrderDate(ORDER_DATE);
    request.setDeliveryTerm(DeliveryTerm.FCA);
    request.setDeliveryPlace("Leeds");
    request.setIncotermsVersion(IncotermsVersion.INCOTERMS_2020);
    request.setDeliveryTermStatus(DeliveryTermStatus.PROPOSED);
    request.setPaymentTerms("30 days");
    request.setContactName("Jane Hill");
    request.setContactEmail("jane@example.com");
    request.setNotes(notes);
    request.setLines(
        new ArrayList<>(List.of(lineRequest(loadLine(l1)), lineRequest(loadLine(l2)))));
    return request;
  }

  private static UpdateSalesOrderLineRequest lineRequest(SalesOrderLine line) {
    return UpdateSalesOrderLineRequest.builder()
        .id(line.getId())
        .productId(line.getProductId())
        .productDesc(line.getProductDesc())
        .colorId(line.getColorId())
        .finishedWidth(line.getFinishedWidth())
        .finishedWidthUnit(line.getFinishedWidthUnit())
        .requestedDeliveryDate(line.getRequestedDeliveryDate())
        .singleLotRequired(line.isSingleLotRequired())
        .requestedQty(line.getRequestedQty())
        .unit(line.getUnit())
        .unitPrice(line.getUnitPriceAmount())
        .currency(line.getCurrency())
        .discountAmount(line.getDiscountAmountValue())
        .taxAmount(line.getTaxAmountValue())
        .toleranceUpPct(line.getToleranceUpPct())
        .toleranceDownPct(line.getToleranceDownPct())
        .moduleType(line.getModuleType())
        .moduleSpecs(line.getModuleSpecs())
        .build();
  }

  private SalesOrderLine loadLine(UUID lineId) {
    Object line = as(actorA, () -> lines.findByTenantIdAndId(tenantId, lineId).orElseThrow());
    if (line instanceof RuntimeException failure) {
      throw failure;
    }
    return (SalesOrderLine) line;
  }
}
