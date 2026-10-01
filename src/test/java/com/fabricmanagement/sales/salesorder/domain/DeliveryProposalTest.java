package com.fabricmanagement.sales.salesorder.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.sales.common.exception.OrderDomainException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeliveryProposalTest {

  private static final UUID ORDER = UUID.randomUUID();
  private static final UUID PLANNER = UUID.randomUUID();
  private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");
  private static final LocalDate TODAY = LocalDate.of(2026, 10, 1);
  private static final DeliveryTerms FCA = DeliveryTerms.of(DeliveryTerm.FCA, "Bursa mill", null);

  private static DeliveryProposal propose(DeliveryTerms terms, LocalDate on, Instant until) {
    return DeliveryProposal.propose(ORDER, null, 1, 0, on, until, terms, null, PLANNER, NOW, TODAY);
  }

  @Test
  void aProposalKeepsTheTermItWasMadeUnderAndItsEvent() {
    DeliveryProposal proposal =
        propose(FCA, LocalDate.of(2026, 10, 20), NOW.plusSeconds(48 * 3600));

    assertThat(proposal.getSequence()).isEqualTo(1);
    assertThat(proposal.getDeliveryEvent()).isEqualTo(DeliveryEvent.HANDED_TO_CARRIER);
    assertThat(proposal.appliesTo(FCA)).isTrue();
    // A changed place changes what the date means: the proposal no longer applies.
    assertThat(proposal.appliesTo(DeliveryTerms.of(DeliveryTerm.FCA, "Istanbul port", null)))
        .isFalse();
    assertThat(proposal.isExpiredAt(NOW)).isFalse();
    assertThat(proposal.isExpiredAt(NOW.plusSeconds(48 * 3600))).isTrue();
  }

  @Test
  void withoutATermThereIsNoEventToProposeADateFor() {
    assertThatThrownBy(
            () -> propose(DeliveryTerms.NONE, LocalDate.of(2026, 10, 20), NOW.plusSeconds(60)))
        .isInstanceOf(OrderDomainException.class)
        .hasMessageContaining("delivery term");
  }

  @Test
  void theDateIsNotInThePastAndTheValidityEndsAfterNow() {
    assertThatThrownBy(() -> propose(FCA, LocalDate.of(2026, 9, 30), NOW.plusSeconds(60)))
        .isInstanceOf(OrderDomainException.class);
    assertThatThrownBy(() -> propose(FCA, LocalDate.of(2026, 10, 20), NOW))
        .isInstanceOf(OrderDomainException.class);
  }

  @Test
  void theFlowMovesOnlyAlongItsPath() {
    assertThat(OrderFlowStage.DRAFT.canMoveTo(OrderFlowStage.AWAITING_PLANNING)).isTrue();
    assertThat(OrderFlowStage.DRAFT.canMoveTo(OrderFlowStage.PLANNED)).isFalse();
    assertThat(OrderFlowStage.IN_PLANNING.canMoveTo(OrderFlowStage.DRAFT)).isTrue();
    assertThat(OrderFlowStage.CUSTOMER_APPROVED.canMoveTo(OrderFlowStage.DRAFT)).isFalse();

    SalesOrder order = SalesOrder.builder().orderNumber("SO-1").build();
    assertThat(order.getFlowStage()).isEqualTo(OrderFlowStage.DRAFT);
    assertThatThrownBy(() -> order.moveFlowTo(OrderFlowStage.PLANNED))
        .isInstanceOf(OrderDomainException.class);
  }
}
