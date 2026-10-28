package com.fabricmanagement.sales.salesorder.app.port;

import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanContent;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/** Consumers rebuild expectations from an explicitly agreed snapshot, never from free text. */
public interface AcceptedPaymentPlanQueryPort {
  Optional<AcceptedPlan> find(UUID tenantId, UUID orderId);

  record AcceptedPlan(
      UUID orderId,
      UUID partnerId,
      UUID planId,
      long planVersion,
      PaymentPlanContent content,
      UUID approvalId,
      Instant agreedAt,
      Instant activatedAt,
      LocalDate confirmationDate,
      boolean cancelled) {}
}
