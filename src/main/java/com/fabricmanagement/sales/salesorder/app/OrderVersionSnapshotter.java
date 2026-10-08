package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.common.infrastructure.tenant.TenantQueryPort;
import com.fabricmanagement.common.infrastructure.tenant.TenantReference;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerService;
import com.fabricmanagement.platform.tradingpartner.dto.TradingPartnerDto;
import com.fabricmanagement.product.color.api.query.ColorQueryService;
import com.fabricmanagement.sales.orderintake.domain.CustomerProductRequest;
import com.fabricmanagement.sales.orderintake.infra.repository.CustomerProductRequestRepository;
import com.fabricmanagement.sales.salesorder.domain.DeliveryProposal;
import com.fabricmanagement.sales.salesorder.domain.OrderCurrencyTotal;
import com.fabricmanagement.sales.salesorder.domain.OrderCurrencyTotals;
import com.fabricmanagement.sales.salesorder.domain.OrderVersionContent;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderLineRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Freezes what the customer is shown of an order: its header, lines with readable product and
 * colour and its dye-lot and shipment preferences, totals per agreed currency, open custom requests
 * and planning's proposal. Internal notes and who did what inside the company are left out.
 */
@Component
public class OrderVersionSnapshotter {

  private final SalesOrderLineRepository lines;
  private final TradingPartnerService partners;
  private final ColorQueryService colours;
  private final CustomerProductRequestRepository requests;
  private final TenantQueryPort tenants;
  private final ObjectMapper canonical;

  public OrderVersionSnapshotter(
      SalesOrderLineRepository lines,
      TradingPartnerService partners,
      ColorQueryService colours,
      CustomerProductRequestRepository requests,
      TenantQueryPort tenants,
      ObjectMapper objectMapper) {
    this.lines = lines;
    this.partners = partners;
    this.colours = colours;
    this.requests = requests;
    this.tenants = tenants;
    this.canonical =
        objectMapper
            .copy()
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
  }

  /** The content and its hash. */
  public record Snapshot(OrderVersionContent content, String hash) {}

  /** The order as the customer reads it now; {@code proposal} is null for an informational copy. */
  public Snapshot snapshot(UUID tenantId, SalesOrder order, DeliveryProposal proposal) {
    List<SalesOrderLine> active =
        lines.findBySalesOrderIdAndIsActiveTrueOrderByCreatedAtAsc(order.getId());
    OrderCurrencyTotals totals = OrderCurrencyTotals.of(active);
    OrderVersionContent content =
        new OrderVersionContent(
            tenants.findById(tenantId).map(TenantReference::name).orElse(null),
            order.getOrderNumber(),
            partners
                .findById(tenantId, order.getTradingPartnerId())
                .map(TradingPartnerDto::getDisplayName)
                .orElse(null),
            order.getCustomerReference(),
            order.getOrderDate(),
            order.getRequestedDeliveryDate(),
            order.getPaymentTerms(),
            new OrderVersionContent.Terms(
                order.getDeliveryTerm(),
                order.getDeliveryPlace(),
                order.getIncotermsVersion(),
                order.getDeliveryEvent(),
                order.getDeliveryTermStatus(),
                order.getDeliveryContractReference()),
            new OrderVersionContent.Contact(
                order.getContactName(), order.getContactEmail(), order.getContactPhone()),
            active.stream().map(this::line).toList(),
            totals.totals().stream().map(OrderVersionSnapshotter::total).toList(),
            totals.unpricedLineCount(),
            requests
                .findByTenantIdAndSalesOrderIdAndIsActiveTrueOrderByRecordedAtAscIdAsc(
                    tenantId, order.getId())
                .stream()
                .filter(request -> !request.getStatus().isFinished())
                .map(OrderVersionSnapshotter::request)
                .toList(),
            proposal == null
                ? null
                : new OrderVersionContent.Proposal(
                    proposal.getId(),
                    proposal.getProposedOn(),
                    proposal.getDeliveryEvent(),
                    proposal.getValidUntil()));
    return new Snapshot(content, hash(content));
  }

  /** SHA-256 of the canonical JSON of the content. */
  public String hash(OrderVersionContent content) {
    try {
      return sha256(canonical.writeValueAsString(content));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("The order version could not be serialised", e);
    }
  }

  static String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }

  private OrderVersionContent.Line line(SalesOrderLine line) {
    return new OrderVersionContent.Line(
        line.getId(),
        line.getProductDesc(),
        line.getColorId() == null
            ? null
            : colours
                .findReferenceById(line.getColorId())
                .map(colour -> label(colour.code(), colour.name()))
                .orElse(null),
        line.getFinishedWidth(),
        line.getFinishedWidthUnit(),
        line.getRequestedQty(),
        line.getUnit(),
        line.getToleranceUpPct(),
        line.getToleranceDownPct(),
        line.getUnitPriceAmount(),
        line.getCurrency(),
        line.getDiscountAmountValue(),
        line.getTaxAmountValue(),
        line.getRequestedDeliveryDate(),
        line.isSingleLotRequired(),
        line.getShipmentPreference());
  }

  private static OrderVersionContent.Total total(OrderCurrencyTotal total) {
    return new OrderVersionContent.Total(
        total.currency(), total.subtotal(), total.discount(), total.tax(), total.grandTotal());
  }

  private static OrderVersionContent.Request request(CustomerProductRequest request) {
    return new OrderVersionContent.Request(
        request.getId(),
        request.getDescription(),
        request.getRequestedQty(),
        request.getUnit(),
        request.getRequestedColorNote(),
        request.getRequestedWidth(),
        request.getRequestedWidthUnit(),
        request.getRequestedDeliveryDate());
  }

  private static String label(String code, String name) {
    if (code == null || code.isBlank()) {
      return name;
    }
    return name == null || name.isBlank() ? code : code + " " + name;
  }
}
