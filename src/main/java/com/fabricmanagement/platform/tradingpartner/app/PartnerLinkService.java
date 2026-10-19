package com.fabricmanagement.platform.tradingpartner.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.tradingpartner.domain.PartnerLink;
import com.fabricmanagement.platform.tradingpartner.domain.PartnerLinkRole;
import com.fabricmanagement.platform.tradingpartner.domain.TradingPartner;
import com.fabricmanagement.platform.tradingpartner.dto.CreatePartnerLinkRequest;
import com.fabricmanagement.platform.tradingpartner.dto.PartnerLinkDto;
import com.fabricmanagement.platform.tradingpartner.dto.PartnerMatchDto;
import com.fabricmanagement.platform.tradingpartner.dto.QuickCreatePartnerLinkRequest;
import com.fabricmanagement.platform.tradingpartner.dto.QuickCreatePartnerLinkResponse;
import com.fabricmanagement.platform.tradingpartner.dto.TradingPartnerDto;
import com.fabricmanagement.platform.tradingpartner.infra.repository.PartnerLinkRepository;
import com.fabricmanagement.platform.tradingpartner.infra.repository.TradingPartnerRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Links a customer to the companies that receive goods for it (CONSIGNEE-1 C1, C2).
 *
 * <p>A link is created two ways: an existing tenant partner is linked, or a company is found or
 * created as a delivery partner and linked in one step. Unlinking is a soft delete, so the same
 * company can be linked again later.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PartnerLinkService {

  public static final String CODE_CUSTOMER_REQUIRED = "PARTNER_LINK_CUSTOMER_REQUIRED";
  public static final String CODE_SELF_LINK = "PARTNER_LINK_SELF";
  public static final String CODE_DUPLICATE = "PARTNER_LINK_DUPLICATE";
  public static final String CODE_PARTNER_INACTIVE = "PARTNER_LINK_PARTNER_INACTIVE";

  private final PartnerLinkRepository linkRepository;
  private final TradingPartnerRepository partnerRepository;
  private final DeliveryPartnerService deliveryPartnerService;

  @Transactional(readOnly = true)
  public List<PartnerLinkDto> list(UUID customerId) {
    UUID tenantId = TenantContext.requireTenantId();
    requireActivePartner(tenantId, customerId);
    return linkRepository.findActiveByTenantIdAndCustomerId(tenantId, customerId).stream()
        .map(PartnerLinkDto::from)
        .toList();
  }

  @Transactional
  public PartnerLinkDto link(UUID customerId, CreatePartnerLinkRequest request) {
    UUID tenantId = TenantContext.requireTenantId();
    TradingPartner customer = requireCustomer(tenantId, customerId);
    TradingPartner linked = requireActivePartner(tenantId, request.getLinkedPartnerId());
    PartnerLink link = createLink(tenantId, customer, linked, request.getRole(), request.getNote());
    return PartnerLinkDto.from(link);
  }

  /**
   * Finds or creates the company and links it, unless similar tenant partners exist and the caller
   * has not confirmed: then nothing is written and the candidates are returned.
   */
  @Transactional
  public QuickCreatePartnerLinkResponse quickCreate(
      UUID customerId, QuickCreatePartnerLinkRequest request) {
    UUID tenantId = TenantContext.requireTenantId();
    TradingPartner customer = requireCustomer(tenantId, customerId);

    if (!Boolean.TRUE.equals(request.getCreateAnyway())) {
      List<PartnerMatchDto> matches =
          deliveryPartnerService.findMatches(tenantId, request, customerId);
      if (!matches.isEmpty()) {
        return QuickCreatePartnerLinkResponse.needsDecision(matches);
      }
    }

    TradingPartner linked = deliveryPartnerService.findOrCreate(request);
    PartnerLink link = createLink(tenantId, customer, linked, request.getRole(), request.getNote());
    return QuickCreatePartnerLinkResponse.linked(
        TradingPartnerDto.from(linked), PartnerLinkDto.from(link));
  }

  @Transactional
  public void unlink(UUID customerId, UUID linkId) {
    UUID tenantId = TenantContext.requireTenantId();
    PartnerLink link =
        linkRepository
            .findActiveByTenantIdAndCustomerIdAndId(tenantId, customerId, linkId)
            .orElseThrow(() -> new NotFoundException("Partner link not found: " + linkId));
    link.delete();
    linkRepository.save(link);
    log.info("Partner link removed: uid={}", link.getUid());
  }

  private PartnerLink createLink(
      UUID tenantId,
      TradingPartner customer,
      TradingPartner linked,
      PartnerLinkRole role,
      String note) {
    if (customer.getId().equals(linked.getId())) {
      throw new PlatformDomainException(
          "A partner cannot be linked to itself", CODE_SELF_LINK, 400);
    }
    if (!Boolean.TRUE.equals(linked.getIsActive())) {
      throw new PlatformDomainException(
          "The company to link is no longer active: " + linked.getId(), CODE_PARTNER_INACTIVE, 409);
    }
    if (linkRepository.countActive(tenantId, customer.getId(), linked.getId(), role) > 0) {
      throw new PlatformDomainException(
          "This company is already linked to the customer as " + role, CODE_DUPLICATE, 409);
    }
    PartnerLink link = PartnerLink.create(customer, linked, role, note);
    link.setTenantId(tenantId);
    PartnerLink saved = linkRepository.save(link);
    log.info(
        "Partner link created: uid={}, customer={}, linked={}, role={}",
        saved.getUid(),
        customer.getUid(),
        linked.getUid(),
        role);
    return saved;
  }

  private TradingPartner requireCustomer(UUID tenantId, UUID customerId) {
    TradingPartner customer = requireActivePartner(tenantId, customerId);
    if (!customer.getPartnerType().isCustomer()) {
      throw new PlatformDomainException(
          "Companies can only be linked to customers: " + customerId, CODE_CUSTOMER_REQUIRED, 400);
    }
    return customer;
  }

  private TradingPartner requireActivePartner(UUID tenantId, UUID partnerId) {
    return partnerRepository
        .findByTenantIdAndId(tenantId, partnerId)
        .filter(partner -> Boolean.TRUE.equals(partner.getIsActive()))
        .orElseThrow(() -> new NotFoundException("Trading partner not found: " + partnerId));
  }
}
