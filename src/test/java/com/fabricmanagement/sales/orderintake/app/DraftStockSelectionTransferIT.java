package com.fabricmanagement.sales.orderintake.app;

import static org.assertj.core.api.Assertions.*;

import com.fabricmanagement.platform.realtime.app.LiveEditLeaseService;
import com.fabricmanagement.production.core.stockunit.api.StockUnitLeaseKeys;
import com.fabricmanagement.production.core.stockunit.infra.repository.StockUnitSoftHoldRepository;
import com.fabricmanagement.sales.orderintake.dto.*;
import com.fabricmanagement.sales.salesorder.app.SalesOrderService;
import com.fabricmanagement.sales.salesorder.dto.*;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderLineRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DraftStockSelectionTransferIT extends DraftStockSelectionITSupport {
  @Autowired SalesOrderService orders;
  @Autowired SalesOrderLineRepository lines;
  @Autowired StockUnitSoftHoldRepository holds;
  @Autowired LiveEditLeaseService leases;
  @Autowired com.fabricmanagement.production.core.stockunit.api.StockUnitSoftHoldPort holdCommands;

  @Autowired
  com.fabricmanagement.production.core.batch.infra.repository.BatchLotQuantityIntentRepository
      intents;

  @Autowired
  com.fabricmanagement.production.core.batch.app.BatchLotQuantityIntentService intentCommands;

  UUID customer;

  @BeforeEach
  void seedCustomer() {
    customer = UUID.randomUUID();
    UUID registry = UUID.randomUUID();
    system.executeInTransaction(
        jdbc -> {
          jdbc.update(
              "insert into common_company.trading_partner_registry(id,uid,official_name,country) values (?,?, 'Northbridge Cloth Ltd','GBR')",
              registry,
              registry.toString());
          jdbc.update(
              "insert into common_company.common_trading_partner(id,tenant_id,uid,registry_id,partner_type) values (?,?,?,?,'CUSTOMER')",
              customer,
              tenant,
              customer.toString(),
              registry);
          return null;
        });
  }

  @Override
  OrderIntakeStockPreviewRequest request() {
    return new OrderIntakeStockPreviewRequest(
        product,
        null,
        null,
        null,
        customer,
        new BigDecimal("700"),
        "M",
        false,
        new BigDecimal("5"),
        new BigDecimal("5"),
        null);
  }

  @Test
  void createTransfersTheSameRollsAndLateReleaseCannotUndoTheOrder() {
    var preview = acquire(open(actor), actor);
    var request = create(preview);
    var saved = in(tenant, () -> orders.createOrder(request));
    UUID lineId = saved.getLines().getFirst().getId();
    var owned = in(tenant, () -> holds.findByTenantIdAndOwnerLineId(tenant, lineId));
    assertThat(owned).hasSize(7).allMatch(row -> row.getOwnerType().equals("SALES_ORDER"));
    assertThat(owned)
        .extracting(row -> row.getStockUnitId())
        .containsExactlyInAnyOrderElementsOf(
            preview.grants().stream().map(DraftStockSelectionDto.Grant::stockUnitId).toList());
    assertThat(in(tenant, () -> leases.held(StockUnitLeaseKeys.resource(product), 1))).isEmpty();
    in(
        tenant,
        () -> {
          service.release(
              preview.selectionId(),
              new DraftStockSelectionRequests.Tokens(
                  preview.selectionRevision(),
                  preview.grants().stream().map(DraftStockSelectionDto.Grant::token).toList()),
              actor);
          return null;
        });
    assertThat(in(tenant, () -> holds.findByTenantIdAndOwnerLineId(tenant, lineId)))
        .allMatch(row -> row.getStatus().name().equals("ACTIVE"));
    var replay = in(tenant, () -> orders.createOrder(request));
    assertThat(replay.getId()).isEqualTo(saved.getId());
    assertThat(in(tenant, () -> holds.findByTenantIdAndOwnerLineId(tenant, lineId))).hasSize(7);
  }

  @Test
  void failureInALaterLineRollsBackTheOrderAndKeepsTheFirstProvisionalSelection() {
    var preview = acquire(open(actor), actor);
    var request = create(preview);
    var invalid =
        SalesOrderLineRequest.builder()
            .productId(product)
            .requestedQty(new BigDecimal("700"))
            .unit("M")
            .requestedDeliveryDate(LocalDate.now().plusDays(1))
            .clientLineId(UUID.randomUUID())
            .toleranceUpPct(new BigDecimal("5"))
            .toleranceDownPct(new BigDecimal("5"))
            .stockSelection(
                new DraftStockSelectionRequests.Proof(
                    UUID.randomUUID(), 0L, List.of(UUID.randomUUID()), true))
            .build();
    request.setLines(List.of(request.getLines().getFirst(), invalid));
    assertThatThrownBy(() -> in(tenant, () -> orders.createOrder(request)))
        .isInstanceOf(com.fabricmanagement.sales.common.exception.OrderIntakeException.class);
    assertThat(in(tenant, () -> service.status(preview.selectionId(), actor)).state())
        .isEqualTo(DraftStockSelectionDto.State.HELD);
    Integer created =
        system.executeQueryForObject(
            "select count(*) from sales_ord.sales_order where tenant_id = ? and creation_key = ?",
            (rs, index) -> rs.getInt(1),
            tenant,
            request.getIdempotencyKey());
    assertThat(created).isZero();
    assertThat(
            in(
                tenant,
                () ->
                    holds.findActiveByStockUnitIds(
                        tenant,
                        preview.grants().stream()
                            .map(DraftStockSelectionDto.Grant::stockUnitId)
                            .toList())))
        .isEmpty();
  }

  @Test
  void unappliedProposalIsReleasedRatherThanSilentlyApplied() {
    var preview = acquire(open(actor), actor);
    var request = create(preview);
    var line = request.getLines().getFirst();
    line.setPreviewStockSelection(line.getStockSelection());
    line.setStockSelection(null);
    var saved = in(tenant, () -> orders.createOrder(request));
    assertThat(
            in(
                tenant,
                () ->
                    holds.findByTenantIdAndOwnerLineId(
                        tenant, saved.getLines().getFirst().getId())))
        .isEmpty();
    assertThat(in(tenant, () -> leases.held(StockUnitLeaseKeys.resource(product), 1))).isEmpty();
  }

  @Test
  void quoteOwnershipMovesInPlaceAndItsOldExpiryCannotReleaseTheOrdersRolls() {
    UUID quote = UUID.randomUUID(),
        sourceLine = UUID.randomUUID(),
        order = UUID.randomUUID(),
        orderLine = UUID.randomUUID();
    var ids = rolls.subList(0, 7);
    var originalHoldIds =
        in(
            tenant,
            () -> {
              intents.save(
                  com.fabricmanagement.production.core.batch.domain.BatchLotQuantityIntent.place(
                      tenant,
                      quote,
                      "QU-2026-001",
                      sourceLine,
                      actor,
                      "Oliver Reed",
                      batch,
                      new BigDecimal("700"),
                      "M",
                      LocalDate.of(2026, 10, 18)));
              holdCommands.replaceHolds(sourceLine, ids);
              return holds.findByTenantIdAndOwnerLineId(tenant, sourceLine).stream()
                  .map(value -> value.getId())
                  .toList();
            });
    UUID intentId =
        in(
            tenant,
            () -> intents.findByTenantIdAndOwnerLineId(tenant, sourceLine).getFirst().getId());
    in(
        tenant,
        () -> {
          holdCommands.transferQuoteHolds(quote, sourceLine, order, "SO-2026-001", orderLine);
          return null;
        });
    var transferred = in(tenant, () -> holds.findByTenantIdAndOwnerLineId(tenant, orderLine));
    assertThat(transferred.stream().map(value -> value.getId()).toList())
        .containsExactlyInAnyOrderElementsOf(originalHoldIds);
    assertThat(transferred)
        .hasSize(7)
        .allMatch(value -> value.getQuoteLineId() == null && value.getOwnerId().equals(order));
    assertThat(
            in(
                tenant,
                () -> intents.findByTenantIdAndOwnerLineId(tenant, orderLine).getFirst().getId()))
        .isEqualTo(intentId);
    assertThat(in(tenant, () -> intentCommands.releaseExpiredIntents(LocalDate.of(2026, 10, 20))))
        .isZero();
    assertThat(in(tenant, () -> holds.findByTenantIdAndOwnerLineId(tenant, orderLine)))
        .allMatch(value -> value.getStatus().name().equals("ACTIVE"));
  }

  private CreateSalesOrderRequest create(DraftStockSelectionDto held) {
    var request = new CreateSalesOrderRequest();
    request.setPartnerId(customer);
    request.setOrderDate(LocalDate.of(2026, 10, 19));
    request.setIdempotencyKey(UUID.randomUUID());
    request.setLines(
        List.of(
            SalesOrderLineRequest.builder()
                .productId(product)
                .requestedQty(new BigDecimal("700"))
                .customerRequestQty(new BigDecimal("700"))
                .unit("M")
                .currency("GBP")
                .unitPrice(new BigDecimal("8.60"))
                .clientLineId(held.clientLineId())
                .toleranceUpPct(new BigDecimal("5"))
                .toleranceDownPct(new BigDecimal("5"))
                .stockSelection(
                    new DraftStockSelectionRequests.Proof(
                        held.selectionId(),
                        held.selectionRevision(),
                        held.grants().stream().map(DraftStockSelectionDto.Grant::token).toList(),
                        true))
                .build()));
    return request;
  }
}
