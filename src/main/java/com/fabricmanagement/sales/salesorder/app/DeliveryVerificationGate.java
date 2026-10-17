package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.DeliveryVerificationPolicy;
import com.fabricmanagement.sales.salesorder.domain.OrderDelivery;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderDeliveryRepository;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Sending an order to the customer for approval needs its delivery details confirmed and a sender
 * who may confirm them (CONSIGNEE-1 C9; READINESS-GATES r3, {@code DELIVERIES_VERIFIED}). The
 * information draft is not gated: sales sends that freely.
 *
 * <p>Every delivery must be {@code VERIFIED}. A delivery can only be verified with a decided
 * consignee and a complete address, except under EXW, where the customer collects and no address is
 * needed; that rule lives in {@link OrderDelivery#verificationBlocker(SalesOrder)} and is applied
 * when the delivery is verified, so this gate only reads the state.
 */
@Component
@RequiredArgsConstructor
public class DeliveryVerificationGate {

  /** The sender does not hold {@code sales:verify-delivery}. */
  public static final String PERMISSION_REQUIRED = "VERIFY_DELIVERY_PERMISSION_REQUIRED";

  /** At least one delivery still waits for verification. */
  public static final String NOT_VERIFIED = "DELIVERIES_NOT_VERIFIED";

  private final OrderDeliveryRepository deliveries;
  private final DeliveryVerificationPolicy verificationPolicy;

  /**
   * Why the order cannot go to the customer for approval on account of its deliveries, or null. The
   * permission comes first: without it nothing the sender does can clear the other reason.
   */
  public String sendBlock(SalesOrder order) {
    if (!verificationPolicy.actorMayVerify()) {
      return PERMISSION_REQUIRED;
    }
    return waiting(order).isEmpty() ? null : NOT_VERIFIED;
  }

  /** The deliveries still to verify and, where known, what each lacks, for the refusal message. */
  public String describe(SalesOrder order) {
    return waiting(order).stream()
        .map(
            delivery ->
                "delivery "
                    + delivery.getSequenceNo()
                    + delivery
                        .verificationBlocker(order)
                        .map(blocker -> " (" + blocker.message() + ")")
                        .orElse(""))
        .collect(Collectors.joining("; "));
  }

  private List<OrderDelivery> waiting(SalesOrder order) {
    return deliveries
        .findByTenantIdAndSalesOrderIdOrderBySequenceNoAsc(
            TenantContext.requireTenantId(), order.getId())
        .stream()
        .filter(delivery -> !delivery.getVerification().isVerified())
        .toList();
  }
}
