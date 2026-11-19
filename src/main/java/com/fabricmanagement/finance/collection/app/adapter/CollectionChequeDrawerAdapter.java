package com.fabricmanagement.finance.collection.app.adapter;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.finance.collection.app.port.CollectionChequeDrawerPort;
import com.fabricmanagement.finance.collection.domain.CollectionTypes.DrawerType;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerChequeDrawerService;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerQueryService;
import com.fabricmanagement.platform.tradingpartner.domain.ChequeDrawerType;
import com.fabricmanagement.platform.tradingpartner.dto.TradingPartnerChequeDrawerDto;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Finance → trading partner, the allowed direction (Rule 11.2); the only class that crosses. */
@Component
@RequiredArgsConstructor
public class CollectionChequeDrawerAdapter implements CollectionChequeDrawerPort {

  private static final int UNSET_DEFAULT_OWN_CHEQUE_PERCENT = 100;

  private final TradingPartnerChequeDrawerService drawers;
  private final TradingPartnerQueryService partners;

  @Override
  public Customer customer(UUID partnerId) {
    UUID tenant = TenantContext.requireTenantId();
    String name =
        partners
            .findDisplayName(tenant, partnerId)
            .orElseThrow(() -> new NotFoundException("Customer not found"));
    int defaultOwn =
        partners
            .findDefaultOwnChequePercent(tenant, partnerId)
            .orElse(UNSET_DEFAULT_OWN_CHEQUE_PERCENT);
    return new Customer(
        name,
        defaultOwn,
        drawers.list(partnerId, false).stream()
            .map(CollectionChequeDrawerAdapter::drawer)
            .toList());
  }

  @Override
  public Added add(UUID partnerId, String name, DrawerType type) {
    var result = drawers.add(partnerId, name, ChequeDrawerType.valueOf(type.name()));
    return new Added(AddOutcome.valueOf(result.outcome().name()), drawer(result.drawer()));
  }

  @Override
  public Drawer reactivate(UUID partnerId, UUID drawerId) {
    return drawer(drawers.reactivate(partnerId, drawerId));
  }

  @Override
  public CustomerStanding standing(UUID partnerId) {
    return partners
        .findClassification(TenantContext.requireTenantId(), partnerId)
        .map(
            partner ->
                !partner.type().isCustomer()
                    ? CustomerStanding.NOT_CUSTOMER
                    : partner.active() ? CustomerStanding.CUSTOMER : CustomerStanding.REMOVED)
        .orElse(CustomerStanding.NOT_FOUND);
  }

  @Override
  public java.util.Optional<String> customerName(UUID partnerId) {
    return partners.findDisplayName(TenantContext.requireTenantId(), partnerId);
  }

  @Override
  public java.util.Optional<Drawer> drawer(UUID partnerId, UUID drawerId) {
    return drawers.find(partnerId, drawerId).map(CollectionChequeDrawerAdapter::drawer);
  }

  private static Drawer drawer(TradingPartnerChequeDrawerDto value) {
    return new Drawer(
        value.id(), value.name(), DrawerType.valueOf(value.drawerType().name()), value.active());
  }
}
