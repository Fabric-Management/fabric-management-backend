package com.fabricmanagement.sales.salesorder.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.sales.common.exception.OrderDomainException;
import org.junit.jupiter.api.Test;

class DeliveryTermsTest {

  @Test
  void eachTermFixesTheEventItsDatesReferTo() {
    // EXW is not FCA: available for collection is not handed to the carrier (loaded).
    assertThat(DeliveryTerm.EXW.event()).isEqualTo(DeliveryEvent.AVAILABLE_FOR_COLLECTION);
    assertThat(DeliveryTerm.FCA.event()).isEqualTo(DeliveryEvent.HANDED_TO_CARRIER);
    assertThat(DeliveryTerm.FOB.event()).isEqualTo(DeliveryEvent.ON_BOARD_VESSEL);
    assertThat(DeliveryTerm.DAP.event())
        .isEqualTo(DeliveryEvent.READY_FOR_UNLOADING_AT_DESTINATION);
    assertThat(DeliveryTerm.DDP.event())
        .isEqualTo(DeliveryEvent.READY_FOR_UNLOADING_AT_DESTINATION);
    assertThat(DeliveryTerm.DPU.event()).isEqualTo(DeliveryEvent.UNLOADED_AT_DESTINATION);
    assertThat(DeliveryTerm.DAP.event().isAtDestination()).isTrue();
    assertThat(DeliveryTerm.FCA.event().isAtDestination()).isFalse();
  }

  @Test
  void underCTermsTheNamedPlaceIsTheDestinationNotWhereDeliveryHappens() {
    assertThat(DeliveryTerm.CPT.event()).isEqualTo(DeliveryEvent.HANDED_TO_CARRIER);
    assertThat(DeliveryTerm.CPT.namedPlaceRole())
        .isEqualTo(DeliveryTerm.NamedPlaceRole.PLACE_OF_DESTINATION);
    assertThat(DeliveryTerm.CIF.namedPlaceRole())
        .isEqualTo(DeliveryTerm.NamedPlaceRole.PORT_OF_DESTINATION);
    assertThat(DeliveryTerm.FCA.namedPlaceRole())
        .isEqualTo(DeliveryTerm.NamedPlaceRole.PLACE_OF_DELIVERY);
  }

  @Test
  void aTermNeedsItsPlaceAndDefaultsToTheCurrentEdition() {
    DeliveryTerms terms = DeliveryTerms.of(DeliveryTerm.FCA, "  Mill gate, Bradford ", null);

    assertThat(terms)
        .isEqualTo(
            new DeliveryTerms(
                DeliveryTerm.FCA, "Mill gate, Bradford", IncotermsVersion.INCOTERMS_2020));
    assertThat(terms.event()).isEqualTo(DeliveryEvent.HANDED_TO_CARRIER);
    assertThatThrownBy(() -> DeliveryTerms.of(DeliveryTerm.EXW, " ", null))
        .isInstanceOf(OrderDomainException.class)
        .hasMessageContaining("named place");
  }

  @Test
  void nothingIsAssumedWithoutATermAndAPlaceAloneIsRejected() {
    assertThat(DeliveryTerms.of(null, null, null)).isEqualTo(DeliveryTerms.NONE);
    assertThat(DeliveryTerms.NONE.isAgreed()).isFalse();
    assertThat(DeliveryTerms.NONE.event()).isNull();
    assertThatThrownBy(() -> DeliveryTerms.of(null, "Mill gate", null))
        .isInstanceOf(OrderDomainException.class);
    assertThatThrownBy(() -> DeliveryTerms.of(null, null, IncotermsVersion.INCOTERMS_2010))
        .isInstanceOf(OrderDomainException.class);
  }

  @Test
  void aTermMustBelongToItsEdition() {
    assertThatThrownBy(
            () -> DeliveryTerms.of(DeliveryTerm.DPU, "Buyer DC", IncotermsVersion.INCOTERMS_2010))
        .isInstanceOf(OrderDomainException.class);
    assertThatThrownBy(() -> DeliveryTerms.of(DeliveryTerm.DAT, "Terminal 2", null))
        .isInstanceOf(OrderDomainException.class);
    assertThat(
            DeliveryTerms.of(DeliveryTerm.DAT, "Terminal 2", IncotermsVersion.INCOTERMS_2010)
                .event())
        .isEqualTo(DeliveryEvent.UNLOADED_AT_DESTINATION);
  }
}
