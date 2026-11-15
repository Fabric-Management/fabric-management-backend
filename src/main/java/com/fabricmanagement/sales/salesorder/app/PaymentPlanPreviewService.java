package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.serialization.CanonicalJsonFingerprint;
import com.fabricmanagement.sales.common.exception.OrderDomainException;
import com.fabricmanagement.sales.salesorder.app.port.PaymentPlanChequeTermsPort;
import com.fabricmanagement.sales.salesorder.domain.OrderCurrencyTotals;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLineStatus;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanContent;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanGenerator;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderPaymentPlanDtos.Preview;
import jakarta.validation.Validator;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Price preview is a calculation, not a persisted order, lease, quotation or payment. */
@Service
@RequiredArgsConstructor
public class PaymentPlanPreviewService {
  private final Validator validator;
  private final PaymentPlanChequeTermsPort chequeTerms;

  public com.fabricmanagement.sales.salesorder.dto.SalesOrderPaymentPlanDtos.PreviewResult preview(
      Preview request) {
    if (!validator.validate(request).isEmpty())
      throw OrderDomainException.rule("PAYMENT_PLAN_INVALID", "Invalid payment plan preview");
    var lines =
        request.lines().stream()
            .map(
                input -> {
                  SalesOrderLine.validatePricing(
                      input.getRequestedQty(),
                      input.getCurrency(),
                      input.getUnitPrice(),
                      input.getDiscountAmount(),
                      input.getTaxAmount());
                  return SalesOrderLine.builder()
                      .requestedQty(input.getRequestedQty())
                      .currency(input.getCurrency())
                      .unitPriceAmount(input.getUnitPrice())
                      .discountAmountValue(input.getDiscountAmount())
                      .taxAmountValue(input.getTaxAmount())
                      .lineStatus(SalesOrderLineStatus.PENDING)
                      .build();
                })
            .toList();
    var totals = OrderCurrencyTotals.of(lines);
    Map<String, java.math.BigDecimal> bases = new TreeMap<>();
    totals.totals().forEach(total -> bases.put(total.currency(), total.grandTotal()));
    UUID tenant = TenantContext.requireTenantId();
    boolean tracked = chequeTerms.chequeSplitTracked(tenant);
    var result =
        PaymentPlanGenerator.generate(
            request.paymentPlan(),
            bases,
            totals.unpricedLineCount() > 0,
            request.orderDate(),
            tracked);
    // The default travels with every result, issues included: the form needs it to complete a
    // missing split (PAYMENT-PLAN-7 R7, R10).
    return new com.fabricmanagement.sales.salesorder.dto.SalesOrderPaymentPlanDtos.PreviewResult(
        new PaymentPlanContent(
            null,
            0,
            CanonicalJsonFingerprint.of(request),
            result.currencyGroups(),
            result.validationIssues()),
        tracked,
        chequeTerms.defaultOwnChequePercent(tenant, request.partnerId()));
  }
}
