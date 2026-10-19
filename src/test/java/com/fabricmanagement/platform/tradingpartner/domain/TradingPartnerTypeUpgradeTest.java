package com.fabricmanagement.platform.tradingpartner.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * CONSIGNEE-1 C3: a delivery partner is neither customer nor supplier and never mixes into BOTH.
 */
class TradingPartnerTypeUpgradeTest {

  private static TradingPartner partnerOf(PartnerType type) {
    return TradingPartner.builder().partnerType(type).build();
  }

  @Test
  void aDeliveryPartnerIsNeitherCustomerNorSupplierNorGroupCompany() {
    PartnerType type = PartnerType.DELIVERY_PARTNER;

    assertThat(type.isDeliveryPartner()).isTrue();
    assertThat(type.isCustomer()).isFalse();
    assertThat(type.isSupplier()).isFalse();
    assertThat(type.isGroupRelationship()).isFalse();
    assertThat(PartnerType.CUSTOMER.isDeliveryPartner()).isFalse();
  }

  @Test
  void aDeliveryPartnerThatBecomesACustomerChangesTypeInsteadOfBecomingBoth() {
    TradingPartner partner = partnerOf(PartnerType.DELIVERY_PARTNER);

    assertThat(partner.upgradeToMultiType(PartnerType.CUSTOMER)).isTrue();

    assertThat(partner.getPartnerType()).isEqualTo(PartnerType.CUSTOMER);
  }

  @Test
  void aDeliveryPartnerThatBecomesASupplierChangesTypeInsteadOfBecomingBoth() {
    TradingPartner partner = partnerOf(PartnerType.DELIVERY_PARTNER);

    assertThat(partner.upgradeToMultiType(PartnerType.SUPPLIER)).isTrue();

    assertThat(partner.getPartnerType()).isEqualTo(PartnerType.SUPPLIER);
  }

  @Test
  void addingTheDeliveryRoleNeverChangesAnExistingRelationship() {
    for (PartnerType existing :
        List.of(
            PartnerType.CUSTOMER,
            PartnerType.SUPPLIER,
            PartnerType.BOTH,
            PartnerType.SUBCONTRACTOR,
            PartnerType.SERVICE_PROVIDER)) {
      TradingPartner partner = partnerOf(existing);

      assertThat(partner.upgradeToMultiType(PartnerType.DELIVERY_PARTNER)).isFalse();

      assertThat(partner.getPartnerType()).isEqualTo(existing);
    }
  }

  @Test
  void differentTradingRelationshipsStillCombineIntoBoth() {
    TradingPartner partner = partnerOf(PartnerType.SUPPLIER);

    assertThat(partner.upgradeToMultiType(PartnerType.CUSTOMER)).isTrue();

    assertThat(partner.getPartnerType()).isEqualTo(PartnerType.BOTH);
  }
}
