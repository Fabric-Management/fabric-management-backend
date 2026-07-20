package com.fabricmanagement.common.infrastructure.events;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.common.domain.event.production.SalesOrderLineProductionCompletedEvent;
import com.fabricmanagement.common.domain.event.production.SalesOrderLineStoredEvent;
import com.fabricmanagement.common.domain.event.production.WorkOrderRecipeAssignmentNeededEvent;
import com.fabricmanagement.common.domain.event.production.WorkOrderStartedEvent;
import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.costing.domain.calculation.CostEntityType;
import com.fabricmanagement.costing.domain.calculation.CostStage;
import com.fabricmanagement.costing.domain.event.CostVarianceDetectedEvent;
import com.fabricmanagement.finance.invoice.domain.event.InvoiceDisputedEvent;
import com.fabricmanagement.finance.invoice.domain.event.InvoiceOverdueEvent;
import com.fabricmanagement.finance.payment.domain.PaymentDirection;
import com.fabricmanagement.finance.payment.domain.event.PaymentReceivedEvent;
import com.fabricmanagement.human.core.employee.domain.event.EmployeeTerminatedEvent;
import com.fabricmanagement.logistics.shipment.domain.event.ShipmentLineConfirmedEvent;
import com.fabricmanagement.platform.approval.domain.event.ApprovalApprovedEvent;
import com.fabricmanagement.platform.approval.domain.event.ApprovalRejectedEvent;
import com.fabricmanagement.platform.tenant.domain.TenantStatus;
import com.fabricmanagement.platform.tenant.domain.event.TenantCreatedEvent;
import com.fabricmanagement.procurement.quote.domain.event.SupplierQuoteAcceptedEvent;
import com.fabricmanagement.production.execution.goodsreceipt.domain.GoodsReceiptSourceType;
import com.fabricmanagement.production.execution.goodsreceipt.domain.event.GoodsReceiptConfirmedEvent;
import com.fabricmanagement.production.execution.output.domain.event.ProductionOutputConfirmedEvent;
import com.fabricmanagement.production.execution.stockunit.domain.PackageType;
import com.fabricmanagement.production.execution.stockunit.domain.event.StockUnitCreatedEvent;
import com.fabricmanagement.production.execution.workorder.domain.WorkOrderModuleType;
import com.fabricmanagement.production.execution.workorder.domain.event.WorkOrderApprovedEvent;
import com.fabricmanagement.production.execution.workorder.domain.event.WorkOrderCompletedEvent;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import com.fabricmanagement.production.quality.result.domain.TestApprovalStatus;
import com.fabricmanagement.production.quality.result.domain.event.FiberTestResultApprovedEvent;
import com.fabricmanagement.sales.quote.domain.event.QuoteSendRequestedEvent;
import com.fabricmanagement.sales.salesorder.domain.event.SalesOrderCancelledEvent;
import com.fabricmanagement.sales.salesorder.domain.event.SalesOrderConfirmedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.context.annotation.Import;
import org.springframework.modulith.events.core.EventSerializer;

@JsonTest
@Import(EventsConfiguration.class)
class DomainEventSerializationTest {

  @Autowired private EventSerializer eventSerializer;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void legacyGoodsReceiptEventWithoutNewFieldsDeserializesWithNulls() throws Exception {
    UUID eventId = uuid();
    String json =
        """
        {
          "eventId":"%s",
          "tenantId":"%s",
          "eventType":"GOODS_RECEIPT_CONFIRMED",
          "occurredAt":"2026-01-01T10:00:00Z",
          "receiptId":"%s",
          "receiptNumber":"GR-LEGACY",
          "sourceType":"PURCHASE_ORDER",
          "sourceId":"%s",
          "items":[{"itemId":"%s","barcode":"BC-1","netWeight":10,"grossWeight":11}]
        }
        """
            .formatted(eventId, uuid(), uuid(), uuid(), uuid());

    GoodsReceiptConfirmedEvent restored =
        objectMapper.readValue(json, GoodsReceiptConfirmedEvent.class);

    assertThat(restored.getEventId()).isEqualTo(eventId);
    assertThat(restored.getSourceLineId()).isNull();
    assertThat(restored.getSupplierBatchCode()).isNull();
    assertThat(restored.getItems().get(0).length()).isNull();
    assertThat(restored.getItems().get(0).lengthUnit()).isNull();
  }

  @Test
  void goodsReceiptEventRoundTripPreservesMaterializationFields() {
    UUID sourceLineId = uuid();
    GoodsReceiptConfirmedEvent original =
        GoodsReceiptConfirmedEvent.builder()
            .tenantId(uuid())
            .receiptId(uuid())
            .receiptNumber("GR-2026-ABC12345")
            .sourceType(GoodsReceiptSourceType.PURCHASE_ORDER)
            .sourceId(uuid())
            .sourceLineId(sourceLineId)
            .supplierBatchCode("LOT-42")
            .items(
                List.of(
                    new GoodsReceiptConfirmedEvent.ReceiptItemData(
                        uuid(),
                        "BC-1",
                        new BigDecimal("10.00"),
                        new BigDecimal("10.50"),
                        new BigDecimal("150"),
                        "CM")))
            .build();

    Object serialized = eventSerializer.serialize(original);
    GoodsReceiptConfirmedEvent restored =
        eventSerializer.deserialize(serialized, GoodsReceiptConfirmedEvent.class);

    assertThat(restored.getSourceLineId()).isEqualTo(sourceLineId);
    assertThat(restored.getSupplierBatchCode()).isEqualTo("LOT-42");
    assertThat(restored.getItems().get(0).length()).isEqualByComparingTo("150");
    assertThat(restored.getItems().get(0).lengthUnit()).isEqualTo("CM");
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("idempotentHandlerConsumedEvents")
  void idempotentHandlerConsumedEventRoundTripPreservesDomainEventEnvelope(
      String eventClassName, DomainEvent original) {
    Object serialized = eventSerializer.serialize(original);
    DomainEvent restored = eventSerializer.deserialize(serialized, original.getClass());

    assertThat(restored.getEventId())
        .as(eventClassName + " eventId")
        .isEqualTo(original.getEventId());
    assertThat(restored.getOccurredAt())
        .as(eventClassName + " occurredAt")
        .isEqualTo(original.getOccurredAt());
    assertThat(restored.getCorrelationId())
        .as(eventClassName + " correlationId")
        .isEqualTo(original.getCorrelationId());
  }

  static List<Arguments> idempotentHandlerConsumedEvents() {
    UUID tenantId = UUID.randomUUID();
    return List.of(
        event(new ApprovalApprovedEvent(tenantId, uuid(), "SALES_ORDER", uuid(), "SO-1", uuid())),
        event(
            new ApprovalRejectedEvent(
                tenantId, uuid(), "WORK_ORDER", uuid(), "WO-1", uuid(), "not approved")),
        event(
            CostVarianceDetectedEvent.builder()
                .tenantId(tenantId)
                .costCalculationId(uuid())
                .entityType(CostEntityType.WORK_ORDER)
                .entityId(uuid())
                .currentStage(CostStage.ACTUAL)
                .previousStage(CostStage.PLANNED)
                .previousTotal(new BigDecimal("100.00"))
                .currentTotal(new BigDecimal("115.00"))
                .varianceRatio(new BigDecimal("0.15"))
                .currency("USD")
                .build()),
        event(new InvoiceOverdueEvent(tenantId, uuid(), "INV-1", uuid(), 7)),
        event(
            new PaymentReceivedEvent(
                tenantId,
                uuid(),
                "PAY-1",
                uuid(),
                PaymentDirection.INBOUND,
                Money.of(100, "USD"),
                "USD")),
        event(new InvoiceDisputedEvent(tenantId, uuid(), "INV-2", uuid())),
        event(
            new SalesOrderConfirmedEvent(
                tenantId,
                uuid(),
                "SO-1",
                uuid(),
                "Customer",
                new BigDecimal("12.50"),
                "kg",
                LocalDate.of(2026, 1, 15),
                List.of(
                    new SalesOrderConfirmedEvent.SalesOrderLineSnapshot(
                        uuid(),
                        uuid(),
                        "FAB-1",
                        new BigDecimal("12.50"),
                        "kg",
                        LocalDate.of(2026, 1, 15))))),
        event(
            new WorkOrderApprovedEvent(
                tenantId,
                uuid(),
                "WO-1",
                WorkOrderModuleType.WEAVING,
                uuid(),
                new BigDecimal("12.50"),
                uuid(),
                uuid())),
        event(
            GoodsReceiptConfirmedEvent.builder()
                .tenantId(tenantId)
                .receiptId(uuid())
                .receiptNumber("GR-1")
                .sourceType(GoodsReceiptSourceType.PURCHASE_ORDER)
                .sourceId(uuid())
                .confirmedAt(Instant.parse("2026-01-01T10:00:00Z"))
                .items(
                    List.of(
                        new GoodsReceiptConfirmedEvent.ReceiptItemData(
                            uuid(),
                            "BC-1",
                            new BigDecimal("10.00"),
                            new BigDecimal("10.50"),
                            null,
                            null)))
                .build()),
        event(new WorkOrderRecipeAssignmentNeededEvent(tenantId, uuid(), uuid(), "GOTS", "TR")),
        event(new QuoteSendRequestedEvent(tenantId, uuid(), uuid(), "Q-1", uuid())),
        event(new TenantSettingsUpdatedEvent(tenantId, "Europe/London", "en-GB", "GBP")),
        event(new EmployeeTerminatedEvent(tenantId, uuid(), LocalDate.of(2026, 2, 1))),
        event(new SupplierQuoteAcceptedEvent(tenantId, uuid(), uuid())),
        event(
            new FiberTestResultApprovedEvent(
                tenantId, uuid(), uuid(), TestApprovalStatus.APPROVED, uuid())),
        event(
            new ProductionOutputConfirmedEvent(
                tenantId,
                uuid(),
                uuid(),
                uuid(),
                uuid(),
                ProductType.FABRIC,
                "m",
                uuid(),
                List.of(
                    new ProductionOutputConfirmedEvent.OutputItemData(
                        uuid(),
                        "OUT-1",
                        PackageType.ROLL,
                        new BigDecimal("8.00"),
                        new BigDecimal("8.20"),
                        uuid())))),
        event(
            new StockUnitCreatedEvent(
                tenantId,
                uuid(),
                "STU-1",
                uuid(),
                ProductType.FABRIC,
                PackageType.ROLL,
                new BigDecimal("8.00"),
                "m",
                uuid())),
        event(
            new WorkOrderCompletedEvent(
                tenantId,
                uuid(),
                uuid(),
                "WO-2",
                new BigDecimal("10.00"),
                new BigDecimal("9.50"),
                new BigDecimal("1.00"),
                new BigDecimal("95.00"),
                Instant.parse("2026-01-02T10:00:00Z"),
                uuid())),
        event(new SalesOrderCancelledEvent(tenantId, uuid(), "SO-2", List.of(uuid()))),
        event(
            new TenantCreatedEvent(
                tenantId,
                "tenant-1",
                "Tenant",
                TenantStatus.TRIAL,
                Instant.parse("2026-02-01T00:00:00Z"))),
        event(new ShipmentLineConfirmedEvent(tenantId, uuid(), uuid(), new BigDecimal("3.25"))),
        event(new WorkOrderStartedEvent(tenantId, uuid(), uuid())),
        event(new SalesOrderLineProductionCompletedEvent(tenantId, uuid(), uuid())),
        event(new SalesOrderLineStoredEvent(tenantId, uuid(), uuid())));
  }

  private static Arguments event(DomainEvent event) {
    return Arguments.of(event.getClass().getName(), event);
  }

  private static UUID uuid() {
    return UUID.randomUUID();
  }
}
