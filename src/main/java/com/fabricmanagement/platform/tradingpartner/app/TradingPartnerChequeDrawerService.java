package com.fabricmanagement.platform.tradingpartner.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.platform.tradingpartner.domain.ChequeDrawerType;
import com.fabricmanagement.platform.tradingpartner.domain.TradingPartner;
import com.fabricmanagement.platform.tradingpartner.domain.TradingPartnerChequeDrawer;
import com.fabricmanagement.platform.tradingpartner.domain.TradingPartnerChequeDrawerException;
import com.fabricmanagement.platform.tradingpartner.dto.TradingPartnerChequeDrawerAddResultDto;
import com.fabricmanagement.platform.tradingpartner.dto.TradingPartnerChequeDrawerAddResultDto.Outcome;
import com.fabricmanagement.platform.tradingpartner.dto.TradingPartnerChequeDrawerDto;
import com.fabricmanagement.platform.tradingpartner.infra.repository.TradingPartnerChequeDrawerRepository;
import com.fabricmanagement.platform.tradingpartner.infra.repository.TradingPartnerRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A customer's cheque drawers (PAYMENT-PLAN-7 R13, R15). Every write locks the partner row first,
 * so two people adding the same name at once get one row. Rows are never deleted.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class TradingPartnerChequeDrawerService {

  private final TradingPartnerRepository partners;
  private final TradingPartnerChequeDrawerRepository drawers;

  /** Every drawer of the customer, or only those offered in the picker. */
  @Transactional(readOnly = true)
  public List<TradingPartnerChequeDrawerDto> list(UUID partnerId, boolean includeInactive) {
    UUID tenant = TenantContext.requireTenantId();
    partners
        .findByTenantIdAndId(tenant, partnerId)
        .orElseThrow(() -> new NotFoundException("Trading partner not found"));
    var rows =
        includeInactive
            ? drawers.findByTenantIdAndPartnerIdOrderByNameAscIdAsc(tenant, partnerId)
            : drawers.findByTenantIdAndPartnerIdAndIsActiveTrueOrderByNameAscIdAsc(
                tenant, partnerId);
    return rows.stream().map(TradingPartnerChequeDrawerDto::from).toList();
  }

  /**
   * Adds a drawer. A name that already exists (after normalisation) is not added again: an active
   * row is returned as {@code EXISTING}; an inactive one is returned unchanged as {@code
   * INACTIVE_DUPLICATE} and is reactivated only by {@link #reactivate}.
   */
  public TradingPartnerChequeDrawerAddResultDto add(
      UUID partnerId, String name, ChequeDrawerType type) {
    UUID tenant = TenantContext.requireTenantId();
    requireCustomer(lock(tenant, partnerId));
    String key = normalise(name);
    var existing = drawers.findByTenantIdAndPartnerIdAndNormalizedName(tenant, partnerId, key);
    if (existing.isPresent()) {
      var row = existing.get();
      return new TradingPartnerChequeDrawerAddResultDto(
          row.isUsable() ? Outcome.EXISTING : Outcome.INACTIVE_DUPLICATE,
          TradingPartnerChequeDrawerDto.from(row));
    }
    var saved = drawers.save(TradingPartnerChequeDrawer.create(partnerId, name, type));
    return new TradingPartnerChequeDrawerAddResultDto(
        Outcome.CREATED, TradingPartnerChequeDrawerDto.from(saved));
  }

  /** Renames or retypes a drawer; cheques keep the name they were received with. */
  public TradingPartnerChequeDrawerDto change(
      UUID partnerId, UUID drawerId, String name, ChequeDrawerType type) {
    UUID tenant = TenantContext.requireTenantId();
    lock(tenant, partnerId);
    var drawer = drawer(tenant, partnerId, drawerId);
    String key = normalise(name);
    drawers
        .findByTenantIdAndPartnerIdAndNormalizedName(tenant, partnerId, key)
        .filter(other -> !other.getId().equals(drawerId))
        .ifPresent(
            other -> {
              throw TradingPartnerChequeDrawerException.nameTaken();
            });
    try {
      drawer.change(name, type);
    } catch (IllegalArgumentException e) {
      throw TradingPartnerChequeDrawerException.invalidName(e.getMessage());
    }
    return TradingPartnerChequeDrawerDto.from(drawers.save(drawer));
  }

  /** Takes the drawer out of the picker. Repeating it changes nothing. */
  public TradingPartnerChequeDrawerDto deactivate(UUID partnerId, UUID drawerId) {
    UUID tenant = TenantContext.requireTenantId();
    lock(tenant, partnerId);
    var drawer = drawer(tenant, partnerId, drawerId);
    drawer.deactivate();
    return TradingPartnerChequeDrawerDto.from(drawers.save(drawer));
  }

  /**
   * Returns the same row to the picker, within the same tenant and customer. Repeating it changes
   * nothing. No cheque or event is rewritten.
   */
  public TradingPartnerChequeDrawerDto reactivate(UUID partnerId, UUID drawerId) {
    UUID tenant = TenantContext.requireTenantId();
    requireCustomer(lock(tenant, partnerId));
    var drawer = drawer(tenant, partnerId, drawerId);
    drawer.reactivate();
    return TradingPartnerChequeDrawerDto.from(drawers.save(drawer));
  }

  private TradingPartner lock(UUID tenant, UUID partnerId) {
    return partners
        .lockByTenantIdAndId(tenant, partnerId)
        .orElseThrow(() -> new NotFoundException("Trading partner not found"));
  }

  private TradingPartnerChequeDrawer drawer(UUID tenant, UUID partnerId, UUID drawerId) {
    return drawers
        .findByTenantIdAndPartnerIdAndId(tenant, partnerId, drawerId)
        .orElseThrow(() -> new NotFoundException("Cheque drawer not found"));
  }

  /** A removed partner, or one that is not a customer, gets no new or returning drawers. */
  private static void requireCustomer(TradingPartner partner) {
    if (!Boolean.TRUE.equals(partner.getIsActive()))
      throw TradingPartnerChequeDrawerException.partnerRemoved();
    if (!partner.getPartnerType().isCustomer())
      throw TradingPartnerChequeDrawerException.notCustomer();
  }

  private static String normalise(String name) {
    try {
      return TradingPartnerChequeDrawer.normalise(name);
    } catch (IllegalArgumentException e) {
      throw TradingPartnerChequeDrawerException.invalidName(e.getMessage());
    }
  }
}
