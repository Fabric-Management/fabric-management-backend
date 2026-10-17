package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.domain.vo.DeliveryVerificationStatus;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.platform.communication.domain.AddressType;
import com.fabricmanagement.platform.organization.api.facade.OrganizationAddressFacade;
import com.fabricmanagement.platform.organization.dto.OrganizationAddressDto;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerService;
import com.fabricmanagement.platform.tradingpartner.dto.TradingPartnerDto;
import com.fabricmanagement.sales.salesorder.dto.ShipToSuggestionDtos.Source;
import com.fabricmanagement.sales.salesorder.dto.ShipToSuggestionDtos.Suggestion;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderDeliveryRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * CONSIGNEE-1 C11: the most used verified card first (the repository hands them over most used
 * first, latest use breaking a tie), else shipping, else primary, else none. A card the consignee
 * no longer has is never offered.
 */
@ExtendWith(MockitoExtension.class)
class ShipToSuggestionServiceTest {

  private static final UUID TENANT = UUID.randomUUID();
  private static final UUID PARTNER = UUID.randomUUID();
  private static final UUID ORGANIZATION = UUID.randomUUID();

  @Mock private OrderDeliveryRepository deliveries;
  @Mock private TradingPartnerService partners;
  @Mock private OrganizationAddressFacade addressBook;
  @InjectMocks private ShipToSuggestionService service;

  @BeforeEach
  void setUp() {
    TenantContext.setCurrentTenantId(TENANT);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  private void partnerWithOrganization() {
    when(partners.findById(TENANT, PARTNER))
        .thenReturn(Optional.of(TradingPartnerDto.builder().organizationId(ORGANIZATION).build()));
  }

  private static OrganizationAddressDto card(
      UUID id, AddressType type, DeliveryVerificationStatus status, boolean primary) {
    return OrganizationAddressDto.builder()
        .organizationId(ORGANIZATION)
        .addressId(id)
        .isPrimary(primary)
        .address(
            OrganizationAddressDto.AddressData.builder()
                .id(id)
                .addressType(type)
                .verificationStatus(status)
                .build())
        .build();
  }

  private void theAddressBookHolds(OrganizationAddressDto... cards) {
    when(addressBook.getOrganizationAddresses(ORGANIZATION)).thenReturn(List.of(cards));
  }

  private void pastDeliveriesUsed(UUID... mostUsedFirst) {
    when(deliveries.shippedToCards(TENANT, PARTNER)).thenReturn(List.of(mostUsedFirst));
  }

  @Test
  void theMostUsedVerifiedCardIsOffered() {
    UUID unverified = UUID.randomUUID();
    UUID mostUsed = UUID.randomUUID();
    UUID lessUsed = UUID.randomUUID();
    partnerWithOrganization();
    theAddressBookHolds(
        card(unverified, AddressType.WAREHOUSE, DeliveryVerificationStatus.UNVERIFIED, false),
        card(lessUsed, AddressType.WAREHOUSE, DeliveryVerificationStatus.VERIFIED, false),
        card(mostUsed, AddressType.WAREHOUSE, DeliveryVerificationStatus.VERIFIED, false));
    pastDeliveriesUsed(unverified, mostUsed, lessUsed);

    assertThat(service.suggest(PARTNER)).isEqualTo(new Suggestion(Source.MOST_USED, mostUsed));
  }

  @Test
  void aCardTheConsigneeNoLongerHasIsSkipped() {
    UUID removed = UUID.randomUUID();
    UUID kept = UUID.randomUUID();
    partnerWithOrganization();
    theAddressBookHolds(
        card(kept, AddressType.WAREHOUSE, DeliveryVerificationStatus.VERIFIED, false));
    pastDeliveriesUsed(removed, kept);

    assertThat(service.suggest(PARTNER)).isEqualTo(new Suggestion(Source.MOST_USED, kept));
  }

  @Test
  void withoutAVerifiedUseTheShippingAddressIsOfferedPreferringAVerifiedOne() {
    UUID unverifiedShipping = UUID.randomUUID();
    UUID verifiedShipping = UUID.randomUUID();
    UUID primary = UUID.randomUUID();
    partnerWithOrganization();
    theAddressBookHolds(
        card(primary, AddressType.OFFICE, DeliveryVerificationStatus.VERIFIED, true),
        card(
            unverifiedShipping, AddressType.SHIPPING, DeliveryVerificationStatus.UNVERIFIED, false),
        card(verifiedShipping, AddressType.SHIPPING, DeliveryVerificationStatus.VERIFIED, false));
    pastDeliveriesUsed();

    assertThat(service.suggest(PARTNER))
        .isEqualTo(new Suggestion(Source.SHIPPING, verifiedShipping));
  }

  @Test
  void anUnverifiedShippingAddressIsStillOfferedWhenNothingBetterExists() {
    UUID shipping = UUID.randomUUID();
    partnerWithOrganization();
    theAddressBookHolds(
        card(shipping, AddressType.SHIPPING, DeliveryVerificationStatus.UNVERIFIED, false));
    pastDeliveriesUsed();

    assertThat(service.suggest(PARTNER)).isEqualTo(new Suggestion(Source.SHIPPING, shipping));
  }

  @Test
  void withoutAShippingAddressThePrimaryOneIsOffered() {
    UUID primary = UUID.randomUUID();
    partnerWithOrganization();
    theAddressBookHolds(
        card(UUID.randomUUID(), AddressType.OFFICE, DeliveryVerificationStatus.VERIFIED, false),
        card(primary, AddressType.HEADQUARTERS, DeliveryVerificationStatus.VERIFIED, true));
    pastDeliveriesUsed();

    assertThat(service.suggest(PARTNER)).isEqualTo(new Suggestion(Source.PRIMARY, primary));
  }

  @Test
  void aConsigneeWithoutAddressCardsGetsNoSuggestion() {
    partnerWithOrganization();
    theAddressBookHolds();
    pastDeliveriesUsed();

    assertThat(service.suggest(PARTNER)).isEqualTo(Suggestion.none());
    assertThat(Suggestion.none().addressId()).isNull();
  }

  @Test
  void aPartnerWithoutAnOrganizationHasNoAddressBookToLookAt() {
    when(partners.findById(TENANT, PARTNER))
        .thenReturn(Optional.of(TradingPartnerDto.builder().build()));

    assertThat(service.suggest(PARTNER)).isEqualTo(Suggestion.none());
    verifyNoInteractions(addressBook, deliveries);
  }

  @Test
  void aPartnerOfAnotherTenantIsNotFound() {
    when(partners.findById(TENANT, PARTNER)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.suggest(PARTNER)).isInstanceOf(NotFoundException.class);
  }
}
