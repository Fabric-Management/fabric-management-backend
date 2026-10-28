package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fabricmanagement.common.infrastructure.tenant.TenantQueryPort;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerService;
import com.fabricmanagement.product.color.api.query.ColorQueryService;
import com.fabricmanagement.sales.orderintake.infra.repository.CustomerProductRequestRepository;
import com.fabricmanagement.sales.salesorder.domain.OrderVersion;
import com.fabricmanagement.sales.salesorder.domain.OrderVersionContent;
import com.fabricmanagement.sales.salesorder.domain.OrderVersionKind;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanContent;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanTypes.DateTrigger;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanTypes.Kind;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanTypes.Method;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderLineRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PaymentPlanSnapshotTest {
  @Test
  void frozenPaymentContentAndHashNeverFollowLaterLiveEdits() throws Exception {
    UUID tenant = UUID.randomUUID();
    var order =
        SalesOrder.builder().tradingPartnerId(UUID.randomUUID()).orderNumber("SO-001").build();
    order.setId(UUID.randomUUID());
    var lines = mock(SalesOrderLineRepository.class);
    var partners = mock(TradingPartnerService.class);
    var requests = mock(CustomerProductRequestRepository.class);
    var tenants = mock(TenantQueryPort.class);
    var plans = mock(SalesOrderPaymentPlanService.class);
    var mapper = new ObjectMapper().findAndRegisterModules();
    var snapshots =
        new OrderVersionSnapshotter(
            lines, partners, mock(ColorQueryService.class), requests, tenants, mapper, plans);
    var original = content(LocalDate.of(2026, 10, 30));
    when(plans.snapshot(order, false)).thenReturn(original);
    var first = snapshots.snapshot(tenant, order, null);
    var version =
        OrderVersion.freeze(
            order.getId(),
            null,
            OrderVersionKind.INFORMATION,
            0,
            0,
            null,
            first.content(),
            first.hash(),
            UUID.randomUUID(),
            Instant.now());
    when(plans.snapshot(order, false)).thenReturn(content(LocalDate.of(2026, 11, 2)));
    var next = snapshots.snapshot(tenant, order, null);
    assertThat(next.hash()).isNotEqualTo(first.hash());
    assertThat(version.getContent().paymentPlan()).isEqualTo(original);
    assertThat(snapshots.hash(version.getContent())).isEqualTo(first.hash());
    assertThat(mapper.writeValueAsString(original)).contains("\"amount\":\"100.00\"");
    assertThat(CustomerApprovalMailer.details(first.content()))
        .contains("100.00", "cash", "30 October 2026");
  }

  @Test
  void historicalVersionsKeepAnAbsentPlanAndDoNotInventRowsFromText() throws Exception {
    var mapper = new ObjectMapper().findAndRegisterModules();
    var legacy =
        new OrderVersionContent(
            "Northbridge Cloth",
            "SO-OLD",
            "Pennine Textiles",
            null,
            null,
            null,
            "Three monthly payments",
            null,
            null,
            List.of(),
            List.of(),
            0,
            List.of(),
            null);
    String json = mapper.writeValueAsString(legacy);
    assertThat(json).doesNotContain("paymentPlan");
    assertThat(mapper.readValue(json, OrderVersionContent.class).paymentPlan()).isNull();
  }

  static PaymentPlanContent content(LocalDate date) {
    return new PaymentPlanContent(
        UUID.randomUUID(),
        1,
        "a".repeat(64),
        List.of(
            new PaymentPlanContent.CurrencyGroup(
                "GBP",
                new BigDecimal("100.00"),
                null,
                List.of(
                    new PaymentPlanContent.Line(
                        UUID.randomUUID(),
                        1,
                        Kind.INSTALMENT,
                        new BigDecimal("100.00"),
                        Method.CASH,
                        DateTrigger.EXPLICIT_DATE,
                        date,
                        null,
                        true)))),
        List.of());
  }
}
