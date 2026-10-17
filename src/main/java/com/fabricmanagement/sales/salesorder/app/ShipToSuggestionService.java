package com.fabricmanagement.sales.salesorder.app;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The address card to offer first when a consignee is chosen (CONSIGNEE-1 C11): the verified card
 * this tenant's deliveries to that consignee used most (the latest use breaks a tie), else the
 * consignee's shipping address, else its primary address, else none. Only cards the consignee still
 * has are considered; a card used before but since removed is never offered.
 */
@Service
@RequiredArgsConstructor
public class ShipToSuggestionService {

  private final OrderDeliveryRepository deliveries;
  private final TradingPartnerService partners;
  private final OrganizationAddressFacade addressBook;

  @Transactional(readOnly = true)
  public Suggestion suggest(UUID partnerId) {
    UUID tenantId = TenantContext.requireTenantId();
    TradingPartnerDto partner =
        partners
            .findById(tenantId, partnerId)
            .orElseThrow(() -> new NotFoundException("Partner not found: " + partnerId));
    if (partner.getOrganizationId() == null) {
      return Suggestion.none();
    }
    Map<UUID, OrganizationAddressDto> cards = new LinkedHashMap<>();
    for (OrganizationAddressDto card :
        addressBook.getOrganizationAddresses(partner.getOrganizationId())) {
      if (card.getAddress() != null) {
        cards.put(card.getAddressId(), card);
      }
    }
    for (UUID used : deliveries.shippedToCards(tenantId, partnerId)) {
      OrganizationAddressDto card = cards.get(used);
      if (card != null && verified(card)) {
        return new Suggestion(Source.MOST_USED, used);
      }
    }
    List<OrganizationAddressDto> shipping =
        cards.values().stream()
            .filter(card -> card.getAddress().getAddressType() == AddressType.SHIPPING)
            .toList();
    if (!shipping.isEmpty()) {
      OrganizationAddressDto chosen =
          shipping.stream()
              .filter(ShipToSuggestionService::verified)
              .findFirst()
              .orElse(shipping.get(0));
      return new Suggestion(Source.SHIPPING, chosen.getAddressId());
    }
    return cards.values().stream()
        .filter(card -> Boolean.TRUE.equals(card.getIsPrimary()))
        .findFirst()
        .map(card -> new Suggestion(Source.PRIMARY, card.getAddressId()))
        .orElseGet(Suggestion::none);
  }

  private static boolean verified(OrganizationAddressDto card) {
    return card.getAddress().getVerificationStatus() == DeliveryVerificationStatus.VERIFIED;
  }
}
