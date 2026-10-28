package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.serialization.CanonicalJsonFingerprint;
import com.fabricmanagement.sales.common.exception.OrderDomainException;
import com.fabricmanagement.sales.salesorder.domain.OrderCurrencyTotals;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.OrderPaymentPlan;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.OrderPaymentPlanCurrency;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.OrderPaymentPlanLine;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.OrderPaymentPlanRevision;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanContent;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanContent.CurrencyGroup;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanContent.Line;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanGenerator;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanInput;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderPaymentPlanDtos.Revision;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderPaymentPlanCurrencyRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderPaymentPlanLineRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderPaymentPlanRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderPaymentPlanRevisionRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderLineRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Normalised persistence and snapshot assembly; never reads customer-visible content from
 * paymentTerms.
 */
@Component
@RequiredArgsConstructor
public class PaymentPlanStore {
  private final OrderPaymentPlanRepository plans;
  private final OrderPaymentPlanRevisionRepository revisions;
  private final OrderPaymentPlanCurrencyRepository currencies;
  private final OrderPaymentPlanLineRepository lines;
  private final SalesOrderLineRepository orderLines;

  public Optional<OrderPaymentPlan> plan(UUID orderId) {
    return plans.findByTenantIdAndSalesOrderId(TenantContext.requireTenantId(), orderId);
  }

  /**
   * Every caller takes the id from the plan's own pointers, or has already compared it with them,
   * so a miss is a broken invariant of the server. It is neither a missing resource nor a state
   * conflict the client could retry away.
   */
  public OrderPaymentPlanRevision revision(OrderPaymentPlan plan, UUID id) {
    return revisions
        .findByTenantIdAndId(TenantContext.requireTenantId(), id)
        .filter(value -> value.getPlanId().equals(plan.getId()))
        .orElseThrow(
            () ->
                OrderDomainException.internal(
                    "PAYMENT_PLAN_REVISION_MISSING",
                    "Payment plan points at a revision that does not belong to it"));
  }

  public List<SalesOrderLine> orderLines(SalesOrder order) {
    return orderLines.findByTenantIdAndSalesOrderIdAndIsActiveTrueOrderByCreatedAtAscIdAsc(
        TenantContext.requireTenantId(), order.getId());
  }

  public PaymentPlanContent content(OrderPaymentPlanRevision revision) {
    return contents(List.of(revision)).get(revision.getId());
  }

  /** Two bounded reads for an entire revision page, including all of its currency groups. */
  public Map<UUID, PaymentPlanContent> contents(List<OrderPaymentPlanRevision> page) {
    if (page.isEmpty()) return Map.of();
    UUID tenant = TenantContext.requireTenantId();
    var groups =
        currencies.findByTenantIdAndRevisionIdInOrderByCurrencyAsc(
            tenant, page.stream().map(OrderPaymentPlanRevision::getId).toList());
    var rows =
        groups.isEmpty()
            ? List.<OrderPaymentPlanLine>of()
            : lines.findByTenantIdAndCurrencyGroupIdInOrderByOrdinalAsc(
                tenant, groups.stream().map(OrderPaymentPlanCurrency::getId).toList());
    var byGroup =
        rows.stream()
            .collect(
                java.util.stream.Collectors.groupingBy(OrderPaymentPlanLine::getCurrencyGroupId));
    var byRevision =
        groups.stream()
            .collect(
                java.util.stream.Collectors.groupingBy(OrderPaymentPlanCurrency::getRevisionId));
    return page.stream()
        .collect(
            java.util.stream.Collectors.toMap(
                OrderPaymentPlanRevision::getId,
                revision ->
                    new PaymentPlanContent(
                        revision.getId(),
                        revision.getRevisionNo(),
                        revision.getPricingFingerprint(),
                        byRevision.getOrDefault(revision.getId(), List.of()).stream()
                            .map(
                                group ->
                                    new CurrencyGroup(
                                        group.getCurrency(),
                                        minor(group.getBaseAmount(), group.getCurrency()),
                                        group.getRules(),
                                        byGroup.getOrDefault(group.getId(), List.of()).stream()
                                            .map(
                                                line -> {
                                                  var row = line.content();
                                                  return new Line(
                                                      row.obligationId(),
                                                      row.ordinal(),
                                                      row.kind(),
                                                      minor(row.amount(), group.getCurrency()),
                                                      row.method(),
                                                      row.dateTrigger(),
                                                      row.expectedDate(),
                                                      row.chequePaymentDate(),
                                                      row.manual());
                                                })
                                            .toList()))
                            .toList(),
                        revision.getValidationIssues())));
  }

  private static java.math.BigDecimal minor(java.math.BigDecimal amount, String currency) {
    return amount == null
        ? null
        : amount.setScale(
            java.util.Currency.getInstance(currency).getDefaultFractionDigits(),
            java.math.RoundingMode.UNNECESSARY);
  }

  public void storeContent(OrderPaymentPlanRevision revision, PaymentPlanContent content) {
    for (var group : content.currencyGroups()) {
      var saved = currencies.save(OrderPaymentPlanCurrency.create(revision.getId(), group));
      lines.saveAll(
          group.lines().stream()
              .map(line -> OrderPaymentPlanLine.create(saved.getId(), line))
              .toList());
    }
  }

  public Revision dto(OrderPaymentPlanRevision revision) {
    return dto(revision, content(revision));
  }

  public Revision dto(OrderPaymentPlanRevision revision, PaymentPlanContent content) {
    return new Revision(
        revision.getId(),
        revision.getRevisionNo(),
        revision.getState(),
        content,
        revision.getResultOrderVersion(),
        revision.getApprovalVersionId(),
        revision.getAgreedApprovalId(),
        revision.getAgreedAt(),
        revision.getActivatedAt(),
        revision.getActivationDate(),
        revision.getCreatedAt(),
        revision.getActorId(),
        revision.getChangeReason());
  }

  public PaymentPlanContent generate(SalesOrder order, PaymentPlanInput input) {
    var totals = OrderCurrencyTotals.of(orderLines(order));
    Map<String, java.math.BigDecimal> bases = new TreeMap<>();
    totals.totals().forEach(value -> bases.put(value.currency(), value.grandTotal()));
    return PaymentPlanGenerator.generate(
        input, bases, totals.unpricedLineCount() > 0, order.getOrderDate());
  }

  public String fingerprint(SalesOrder order) {
    var facts = new TreeMap<String, Object>();
    facts.put("partnerId", order.getTradingPartnerId());
    facts.put("orderDate", order.getOrderDate());
    var active = orderLines(order);
    facts.put("totals", OrderCurrencyTotals.of(active));
    facts.put(
        "lines",
        active.stream()
            .map(
                line -> {
                  var amounts = new TreeMap<String, Object>();
                  amounts.put("id", line.getId());
                  amounts.put("quantity", line.getRequestedQty());
                  amounts.put("currency", line.getCurrency());
                  amounts.put("unitPrice", line.getUnitPriceAmount());
                  amounts.put("discount", line.getDiscountAmountValue());
                  amounts.put("tax", line.getTaxAmountValue());
                  // Fulfilment progress does not renegotiate commercial terms. Cancellation does.
                  amounts.put(
                      "status",
                      line.getLineStatus()
                              == com.fabricmanagement.sales.salesorder.domain.SalesOrderLineStatus
                                  .CANCELLED
                          ? com.fabricmanagement.sales.salesorder.domain.SalesOrderLineStatus
                              .CANCELLED
                          : com.fabricmanagement.sales.salesorder.domain.SalesOrderLineStatus
                              .PENDING);
                  return amounts;
                })
            .toList());
    return CanonicalJsonFingerprint.of(facts);
  }

  public boolean stale(SalesOrder order, OrderPaymentPlanRevision revision) {
    return !revision.getPricingFingerprint().equals(fingerprint(order));
  }

  public void assertReady(SalesOrder order, OrderPaymentPlanRevision revision) {
    if (stale(order, revision))
      throw OrderDomainException.conflict(
          "PAYMENT_PLAN_STALE", "Reconcile the payment plan with the current order");
    if (!revision.getValidationIssues().isEmpty())
      throw OrderDomainException.conflict(
              "PAYMENT_PLAN_INCOMPLETE",
              "Complete the payment plan before requesting agreement: "
                  + String.join(", ", revision.getValidationIssues()))
          .withDetail("validationIssues", revision.getValidationIssues());
  }
}
