package com.fabricmanagement.common.platform.company.app;

import com.fabricmanagement.common.platform.company.domain.TradingPartner;
import com.fabricmanagement.common.platform.company.infra.repository.TradingPartnerRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Helper for resolving partner IDs during Faz 1.5 migration transition.
 *
 * <p>Use this component in any service that needs to handle both:
 *
 * <ul>
 *   <li>New TradingPartner IDs (trading_partner_id)
 *   <li>Legacy Company IDs (company_id mapped via legacy_company_id)
 * </ul>
 *
 * <h2>Usage in Services:</h2>
 *
 * <pre>{@code
 * @Service
 * @RequiredArgsConstructor
 * public class OrderService {
 *     private final TradingPartnerResolver partnerResolver;
 *
 *     public void createOrder(CreateOrderRequest request) {
 *         UUID tenantId = TenantContext.getCurrentTenantId();
 *         // Resolves both new and legacy partner IDs
 *         UUID partnerId = partnerResolver.resolvePartnerId(tenantId, request.getPartnerId());
 *         // Use resolved partnerId...
 *     }
 * }
 * }</pre>
 *
 * @see TradingPartnerService for partner lifecycle operations
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TradingPartnerResolver {

  private final TradingPartnerRepository partnerRepository;

  @Value("${feature.trading-partner.legacy-fallback:true}")
  private boolean legacyFallbackEnabled;

  /**
   * Resolve partner ID - handles both new and legacy IDs.
   *
   * <p>Resolution order:
   *
   * <ol>
   *   <li>Check if partnerId is a TradingPartner.id
   *   <li>Check if partnerId is a legacy Company.id (via legacy_company_id)
   * </ol>
   *
   * @param tenantId Current tenant
   * @param partnerId Could be TradingPartner.id or legacy Company.id
   * @return TradingPartner ID
   * @throws IllegalArgumentException if partner not found
   */
  public UUID resolvePartnerId(UUID tenantId, UUID partnerId) {
    if (partnerId == null) {
      throw new IllegalArgumentException("Partner ID cannot be null");
    }

    // 1. Check if it's already a TradingPartner ID
    if (partnerRepository.existsByTenantIdAndId(tenantId, partnerId)) {
      return partnerId;
    }

    // 2. Check if it's a legacy company ID
    Optional<TradingPartner> legacyMatch =
        partnerRepository.findByTenantIdAndLegacyCompanyId(tenantId, partnerId);

    if (legacyMatch.isPresent()) {
      log.debug(
          "Resolved legacy company ID {} to trading partner ID {}",
          partnerId,
          legacyMatch.get().getId());
      return legacyMatch.get().getId();
    }

    throw new IllegalArgumentException(
        "Trading partner not found: "
            + partnerId
            + ". Not found as TradingPartner.id or legacy_company_id.");
  }

  /**
   * Try to resolve partner ID without throwing exception.
   *
   * @param tenantId Current tenant
   * @param partnerId Could be TradingPartner.id or legacy Company.id
   * @return Optional containing resolved TradingPartner ID, or empty if not found
   */
  public Optional<UUID> tryResolvePartnerId(UUID tenantId, UUID partnerId) {
    if (partnerId == null) {
      return Optional.empty();
    }

    // 1. Check if it's already a TradingPartner ID
    if (partnerRepository.existsByTenantIdAndId(tenantId, partnerId)) {
      return Optional.of(partnerId);
    }

    // 2. Check if it's a legacy company ID
    return partnerRepository
        .findByTenantIdAndLegacyCompanyId(tenantId, partnerId)
        .map(TradingPartner::getId);
  }

  /**
   * Get effective partner ID from entity during transition.
   *
   * <p>Use this when reading an entity that may have:
   *
   * <ul>
   *   <li>trading_partner_id set (new records, migrated records)
   *   <li>only company_id set (unmigrated records)
   * </ul>
   *
   * @param tradingPartnerId New FK (may be null during transition)
   * @param companyId Legacy FK (for unmigrated records)
   * @param tenantId Current tenant
   * @return Resolved TradingPartner ID
   * @throws IllegalStateException if legacy fallback disabled and trading_partner_id is null
   */
  public UUID getEffectivePartnerId(UUID tradingPartnerId, UUID companyId, UUID tenantId) {
    // New FK takes precedence
    if (tradingPartnerId != null) {
      return tradingPartnerId;
    }

    // No trading_partner_id - check if legacy fallback is enabled
    if (!legacyFallbackEnabled) {
      throw new IllegalStateException(
          "Legacy fallback disabled but trading_partner_id is null. "
              + "Record needs migration: company_id="
              + companyId);
    }

    // Legacy fallback - try to resolve company_id
    if (companyId == null) {
      throw new IllegalArgumentException(
          "Both trading_partner_id and company_id are null. Invalid record.");
    }

    return partnerRepository
        .findByTenantIdAndLegacyCompanyId(tenantId, companyId)
        .map(TradingPartner::getId)
        .orElse(companyId); // Return company_id if no mapping (edge case)
  }

  /**
   * Get TradingPartner entity for a resolved ID.
   *
   * <p>Useful when you need the full TradingPartner object, not just the ID.
   *
   * @param tenantId Current tenant
   * @param partnerId TradingPartner ID or legacy Company ID
   * @return TradingPartner entity
   */
  public Optional<TradingPartner> resolvePartner(UUID tenantId, UUID partnerId) {
    if (partnerId == null) {
      return Optional.empty();
    }

    // 1. Try direct ID lookup
    Optional<TradingPartner> direct = partnerRepository.findByTenantIdAndId(tenantId, partnerId);
    if (direct.isPresent()) {
      return direct;
    }

    // 2. Try legacy ID lookup
    return partnerRepository.findByTenantIdAndLegacyCompanyId(tenantId, partnerId);
  }

  /**
   * Check if legacy fallback is currently enabled.
   *
   * <p>Use this for conditional logic in services:
   *
   * <pre>{@code
   * if (partnerResolver.isLegacyFallbackEnabled()) {
   *     // Also set company_id for backward compatibility
   *     order.setCompanyId(partner.getLegacyCompanyId());
   * }
   * }</pre>
   *
   * @return true if legacy fallback is enabled
   */
  public boolean isLegacyFallbackEnabled() {
    return legacyFallbackEnabled;
  }

  /**
   * Get the legacy Company ID from a TradingPartner (if migrated).
   *
   * <p>Use this for dual-write scenarios during transition.
   *
   * @param tenantId Current tenant
   * @param tradingPartnerId TradingPartner ID
   * @return Legacy Company ID if exists, null otherwise
   */
  public UUID getLegacyCompanyId(UUID tenantId, UUID tradingPartnerId) {
    return partnerRepository
        .findByTenantIdAndId(tenantId, tradingPartnerId)
        .map(TradingPartner::getLegacyCompanyId)
        .orElse(null);
  }
}
