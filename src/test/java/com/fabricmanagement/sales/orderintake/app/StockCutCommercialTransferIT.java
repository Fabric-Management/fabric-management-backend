package com.fabricmanagement.sales.orderintake.app;

import static org.assertj.core.api.Assertions.*;

import com.fabricmanagement.production.core.stockunit.api.StockUnitCutTargetPort;
import com.fabricmanagement.production.core.stockunit.dto.StockUnitCutPlanDtos;
import com.fabricmanagement.sales.common.domain.SalesQuantityPolicy;
import com.fabricmanagement.sales.orderintake.dto.*;
import com.fabricmanagement.sales.quote.dto.QuoteRollSelectionRequests;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StockCutCommercialTransferIT extends StockCutITSupport {
  @Override
  OrderIntakeStockPreviewRequest request() {
    var original = super.request();
    return new OrderIntakeStockPreviewRequest(
        original.productId(),
        original.colorId(),
        original.finishedWidth(),
        original.finishedWidthUnit(),
        original.customerId(),
        original.requestedQty(),
        original.unit(),
        original.singleLotRequired(),
        original.toleranceUpPct(),
        original.toleranceDownPct(),
        original.manualPieceIds(),
        null,
        SalesQuantityPolicy.EXACT);
  }

  DraftStockSelectionRequests.Proof cutProof(DraftStockSelectionDto held) {
    var ordinary = proof(held);
    return new DraftStockSelectionRequests.Proof(
        ordinary.selectionId(),
        ordinary.selectionRevision(),
        ordinary.tokens(),
        false,
        rolls.get(16));
  }

  @Test
  void anExplicitDraftCutPreviewCanAttachSourcesWithoutIncreasingCommercialQuantity() {
    var session =
        in(
            tenant,
            () ->
                service.open(
                    new DraftStockSelectionRequests.Open(
                        UUID.randomUUID(), UUID.randomUUID(), product, null, null),
                    actor));
    var held = holdQuote(session);
    var proof = cutProof(held);
    var evaluation = in(tenant, () -> service.previewCut(held.selectionId(), proof, actor));
    assertThat(evaluation.planId()).isNull();
    assertThat(evaluation.sourceQuantity()).isEqualByComparingTo("2125");
    assertThat(evaluation.deliveryQuantity()).isEqualByComparingTo("2050");
    assertThat(evaluation.remainderQuantity()).isEqualByComparingTo("75");
    var create = metreOrder(SalesQuantityPolicy.EXACT);
    create.getLines().getFirst().setClientLineId(held.clientLineId());
    create.getLines().getFirst().setStockSelection(proof);
    var saved = in(tenant, () -> orders.createOrder(create));
    assertThat(saved.getLines().getFirst().getRequestedQty()).isEqualByComparingTo("2050");
    var plan =
        in(
                tenant,
                () ->
                    cutPlans.list(
                        new StockUnitCutTargetPort.Target(
                            StockUnitCutTargetPort.TargetType.SALES_ORDER,
                            saved.getId(),
                            saved.getLines().getFirst().getId()),
                        actor))
            .getFirst();
    assertThat(plan.sourceQuantity()).isEqualByComparingTo("2125");
    assertThat(plan.state().name()).isEqualTo("PREPARED");
    assertThat(plan.deliveryStockUnitId()).isNull();
  }

  @Test
  void quoteConversionMovesTheSameCutPlanAndWholeSourceOwnership() {
    system.executeInTransaction(
        jdbc -> {
          jdbc.update(
              "update sales.quote_line set quantity_policy='EXACT', customer_request_qty=2050 where id=?",
              quoteLineId);
          return null;
        });
    var held = holdQuote(quoteSession());
    var proof = cutProof(held);
    in(tenant, () -> service.previewCut(held.selectionId(), proof, actor));
    var applied =
        in(
            tenant,
            () ->
                quoteCommands.apply(
                    quoteId,
                    quoteLineId,
                    new QuoteRollSelectionRequests.Apply(UUID.randomUUID(), quoteVersion(), proof),
                    actor));
    assertThat(applied.commercialQuantity()).isEqualByComparingTo("2050");
    var before =
        in(
                tenant,
                () ->
                    cutPlans.list(
                        new StockUnitCutTargetPort.Target(
                            StockUnitCutTargetPort.TargetType.QUOTE, quoteId, quoteLineId),
                        actor))
            .getFirst();
    in(
        tenant,
        () -> {
          var quote = quotes.findByTenantIdAndIdAndIsActiveTrue(tenant, quoteId).orElseThrow();
          quote.setStatus(com.fabricmanagement.sales.quote.domain.QuoteStatus.APPROVED);
          quotes.saveAndFlush(quote);
          return null;
        });
    var create = metreOrder(SalesQuantityPolicy.EXACT);
    create.setQuoteId(quoteId);
    create.getLines().getFirst().setSourceQuoteLineId(quoteLineId);
    var order = in(tenant, () -> orders.createOrder(create));
    var after = in(tenant, () -> cutPlans.get(before.planId(), actor));
    assertThat(after.planId()).isEqualTo(before.planId());
    assertThat(after.targetType()).isEqualTo(StockUnitCutTargetPort.TargetType.SALES_ORDER);
    assertThat(after.lineId()).isEqualTo(order.getLines().getFirst().getId());
    assertThat(after.sources())
        .extracting(StockUnitCutPlanDtos.Source::stockUnitId)
        .containsExactlyInAnyOrderElementsOf(rolls.subList(0, 17));
    assertThat(order.getLines().getFirst().getRequestedQty()).isEqualByComparingTo("2050");
  }

  @Test
  void aLegacyCutCannotBypassTheWholeSourceOwnership() {
    var order = createExact();
    apply(pick(openSources(order)));
    assertThatThrownBy(
            () ->
                in(
                    tenant,
                    () ->
                        legacyCuts.recordCut(
                            rolls.get(16),
                            new com.fabricmanagement.production.core.stockunit.dto.StockUnitCutDtos
                                .RecordCutRequest(new BigDecimal("50"), new BigDecimal("75")))))
        .isInstanceOf(
            com.fabricmanagement.common.infrastructure.web.exception.DomainException.class);
  }
}
