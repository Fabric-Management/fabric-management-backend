package com.fabricmanagement.platform.tradingpartner.app;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.organization.api.facade.OrganizationFacade;
import com.fabricmanagement.platform.organization.dto.OrganizationDto;
import com.fabricmanagement.platform.tradingpartner.domain.PartnerMatchReason;
import com.fabricmanagement.platform.tradingpartner.domain.PartnerType;
import com.fabricmanagement.platform.tradingpartner.domain.TradingPartner;
import com.fabricmanagement.platform.tradingpartner.domain.TradingPartnerRegistry;
import com.fabricmanagement.platform.tradingpartner.domain.event.TradingPartnerCreatedEvent;
import com.fabricmanagement.platform.tradingpartner.dto.PartnerMatchDto;
import com.fabricmanagement.platform.tradingpartner.dto.QuickCreatePartnerLinkRequest;
import com.fabricmanagement.platform.tradingpartner.infra.repository.TradingPartnerRegistryRepository;
import com.fabricmanagement.platform.tradingpartner.infra.repository.TradingPartnerRepository;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Finds and creates the companies that only receive goods for a customer (CONSIGNEE-1 C3, C4).
 *
 * <p>A {@link PartnerType#DELIVERY_PARTNER} has no commercial relationship with the tenant. It is
 * never offered as a customer or supplier; if it later becomes one, its partner type changes on the
 * same record and its links and addresses stay.
 *
 * <h2>Duplicates</h2>
 *
 * <p>Matches are looked up among this tenant's own partners only (tax id first, then name). The
 * shared registry deduplicates by exact tax id and country when a record is created, but its names
 * are never listed: they belong to other tenants. The {@code (tenant_id, registry_id)} uniqueness
 * stays the hard guard: a company the tenant already has is reused as it is, with its type
 * untouched.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryPartnerService {

  private final TradingPartnerRepository partnerRepository;
  private final TradingPartnerRegistryRepository registryRepository;
  private final TradingPartnerRegistryService registryService;
  private final OrganizationFacade organizationFacade;
  private final DomainEventPublisher eventPublisher;

  /**
   * Active tenant partners that look like the company in the request.
   *
   * @param excludePartnerId a partner that must not be offered (the customer being linked)
   */
  @Transactional(readOnly = true)
  public List<PartnerMatchDto> findMatches(
      UUID tenantId, QuickCreatePartnerLinkRequest request, UUID excludePartnerId) {
    Map<UUID, PartnerMatchDto> matches = new LinkedHashMap<>();

    String taxId = blankToNull(request.getTaxId());
    if (taxId != null) {
      registryRepository
          .findByTaxIdAndCountry(taxId, country(request))
          .flatMap(
              registry -> partnerRepository.findByTenantIdAndRegistryId(tenantId, registry.getId()))
          .filter(partner -> Boolean.TRUE.equals(partner.getIsActive()))
          .ifPresent(
              partner ->
                  matches.putIfAbsent(
                      partner.getId(), PartnerMatchDto.of(partner, PartnerMatchReason.TAX_ID)));
    }

    partnerRepository
        .searchByName(tenantId, request.getCompanyName().strip())
        .forEach(
            partner ->
                matches.putIfAbsent(
                    partner.getId(), PartnerMatchDto.of(partner, PartnerMatchReason.NAME)));

    matches.remove(excludePartnerId);
    return List.copyOf(matches.values());
  }

  /**
   * Returns the tenant's partner for the entered company, creating a delivery partner when the
   * tenant has none for that registry record.
   */
  @Transactional
  public TradingPartner findOrCreate(QuickCreatePartnerLinkRequest request) {
    UUID tenantId = TenantContext.requireTenantId();
    String companyName = request.getCompanyName().strip();
    TradingPartnerRegistry registry =
        registryService.findOrCreate(
            blankToNull(request.getTaxId()), companyName, country(request));

    Optional<TradingPartner> existing =
        partnerRepository.findByTenantIdAndRegistryId(tenantId, registry.getId());
    if (existing.isPresent()) {
      log.debug(
          "Delivery partner request reuses existing partner: uid={}, type={}",
          existing.get().getUid(),
          existing.get().getPartnerType());
      return existing.get();
    }

    TradingPartner partner =
        TradingPartner.create(registry, PartnerType.DELIVERY_PARTNER, companyName);
    partner.setRelationshipMeta(relationshipMeta(request));
    TradingPartner saved = partnerRepository.save(partner);

    // The partner organization carries the address book (CONSIGNEE-1 S4 reads it).
    OrganizationDto partnerOrg =
        organizationFacade.createPartnerOrganization(
            saved.getDisplayName(), registry.getTaxId(), saved.getUid());
    saved.setOrganizationId(partnerOrg.getId());
    saved = partnerRepository.save(saved);

    eventPublisher.publish(
        new TradingPartnerCreatedEvent(
            tenantId,
            saved.getId(),
            registry.getId(),
            saved.getPartnerType().name(),
            saved.getDisplayName(),
            null));
    log.info(
        "Delivery partner created: uid={}, registry={}, organizationId={}",
        saved.getUid(),
        registry.getUid(),
        partnerOrg.getId());
    return saved;
  }

  private Map<String, Object> relationshipMeta(QuickCreatePartnerLinkRequest request) {
    Map<String, Object> meta = new HashMap<>();
    putIfPresent(meta, "quick_create_city", request.getCity());
    putIfPresent(meta, "quick_create_phone", request.getPhone());
    meta.put("quick_created", true);
    return meta;
  }

  private static void putIfPresent(Map<String, Object> meta, String key, String value) {
    String text = blankToNull(value);
    if (text != null) {
      meta.put(key, text);
    }
  }

  private static String country(QuickCreatePartnerLinkRequest request) {
    return request.getCountry().strip().toUpperCase(Locale.ROOT);
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }
}
