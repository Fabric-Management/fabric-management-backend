package com.fabricmanagement.sales.salesorder.domain.paymentplan;

import static org.assertj.core.api.Assertions.*;

import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderPaymentPlanRevisionTest {
  @Test
  void onlyTheProposedFrozenVersionCanBecomeAgreed() {
    var revision =
        OrderPaymentPlanRevision.create(
            UUID.randomUUID(),
            1,
            0,
            "a".repeat(64),
            UUID.randomUUID(),
            "b".repeat(64),
            UUID.randomUUID(),
            "Initial agreement",
            List.of());
    UUID version = UUID.randomUUID(), approval = UUID.randomUUID();
    assertThatThrownBy(() -> revision.agree(version, approval, Instant.now()))
        .hasMessageContaining("cannot change state");
    revision.propose(version);
    assertThatThrownBy(() -> revision.agree(UUID.randomUUID(), approval, Instant.now()))
        .hasMessageContaining("cannot change state");
    Instant now = Instant.parse("2026-10-21T09:00:00Z");
    revision.agree(version, approval, now);
    assertThatThrownBy(() -> revision.agree(version, approval, now.plusSeconds(10)))
        .hasMessageContaining("cannot change state");
    assertThat(revision.getAgreedAt()).isEqualTo(now);
    assertThat(revision.getState()).isEqualTo(PaymentPlanTypes.State.AGREED);
    revision.supersede();
    assertThat(revision.getState()).isEqualTo(PaymentPlanTypes.State.SUPERSEDED);
    assertThat(revision.getAgreedApprovalId()).isEqualTo(approval);
  }

  @Test
  void aReplacedDraftOrProposalIsSupersededButAnAgreementOrCancellationIsNotTouched() {
    var draft = revision();
    draft.replaced();
    assertThat(draft.getState()).isEqualTo(PaymentPlanTypes.State.SUPERSEDED);

    var proposed = revision();
    proposed.propose(UUID.randomUUID());
    proposed.replaced();
    assertThat(proposed.getState()).isEqualTo(PaymentPlanTypes.State.SUPERSEDED);

    var agreed = revision();
    UUID version = UUID.randomUUID();
    agreed.propose(version);
    agreed.agree(version, UUID.randomUUID(), Instant.parse("2026-10-21T09:00:00Z"));
    agreed.replaced();
    assertThat(agreed.getState()).isEqualTo(PaymentPlanTypes.State.AGREED);

    var cancelled = revision();
    cancelled.cancel(Instant.parse("2026-10-21T09:00:00Z"));
    cancelled.replaced();
    assertThat(cancelled.getState()).isEqualTo(PaymentPlanTypes.State.CANCELLED);
  }

  private static OrderPaymentPlanRevision revision() {
    return OrderPaymentPlanRevision.create(
        UUID.randomUUID(),
        1,
        0,
        "a".repeat(64),
        UUID.randomUUID(),
        "b".repeat(64),
        UUID.randomUUID(),
        "Revision under test",
        List.of());
  }

  @Test
  void legacyPaymentTermsStayEditableButStructuredSummaryHasNoSafeEditBackDoor() {
    var order = SalesOrder.builder().paymentTerms("Payment on delivery").build();
    order.setPaymentTerms("Payment by bank transfer");
    order.applyPaymentPlanSummary("GBP 100: 3 payments");
    assertThat(order.getPaymentNote()).isEqualTo("Payment by bank transfer");
    assertThatThrownBy(() -> order.setPaymentTerms("Paid in full"))
        .hasMessageContaining("structured payment plan");
    assertThat(order.getPaymentTerms()).isEqualTo("GBP 100: 3 payments");
  }
}
