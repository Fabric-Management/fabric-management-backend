package com.fabricmanagement.platform.tradingpartner.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.CommonDomainException;
import com.fabricmanagement.platform.tradingpartner.domain.PartnerContact;
import com.fabricmanagement.platform.tradingpartner.domain.PartnerContactRole;
import com.fabricmanagement.platform.tradingpartner.domain.TradingPartner;
import com.fabricmanagement.platform.tradingpartner.dto.CreatePartnerContactRequest;
import com.fabricmanagement.platform.tradingpartner.dto.PartnerContactDto;
import com.fabricmanagement.platform.tradingpartner.infra.repository.PartnerContactRepository;
import com.fabricmanagement.platform.tradingpartner.infra.repository.TradingPartnerRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PartnerContactService {

  private final PartnerContactRepository contactRepository;
  private final TradingPartnerRepository partnerRepository;

  @Transactional(readOnly = true)
  public List<PartnerContactDto> listContacts(UUID partnerId, PartnerContactRole role) {
    UUID tenantId = TenantContext.requireTenantId();
    requirePartner(tenantId, partnerId);
    List<PartnerContact> contacts =
        role == null
            ? contactRepository.findActiveByTenantIdAndPartnerId(tenantId, partnerId)
            : contactRepository.findActiveByTenantIdAndPartnerIdAndRole(tenantId, partnerId, role);
    return contacts.stream().map(PartnerContactDto::from).toList();
  }

  @Transactional
  public PartnerContactDto createContact(UUID partnerId, CreatePartnerContactRequest request) {
    UUID tenantId = TenantContext.requireTenantId();
    TradingPartner partner = requirePartner(tenantId, partnerId);
    PartnerContact contact =
        createContact(
            tenantId,
            partner,
            request.getName(),
            request.getEmail(),
            request.getPhone(),
            request.getRole(),
            Boolean.TRUE.equals(request.getWhatsappEnabled()),
            Boolean.TRUE.equals(request.getPrimary()));
    return PartnerContactDto.from(contact);
  }

  @Transactional(readOnly = true)
  public PartnerContact requireActiveContact(UUID tenantId, UUID contactId) {
    return contactRepository
        .findActiveByTenantIdAndId(tenantId, contactId)
        .orElseThrow(() -> new CommonDomainException("Partner contact not found: " + contactId));
  }

  PartnerContact createContact(
      UUID tenantId,
      TradingPartner partner,
      String name,
      String email,
      String phone,
      PartnerContactRole role,
      boolean whatsappEnabled,
      boolean primary) {
    if (primary) {
      demoteExistingPrimary(tenantId, partner.getId(), role);
    }
    PartnerContact contact =
        PartnerContact.create(partner, name, email, phone, role, whatsappEnabled, primary);
    contact.setTenantId(tenantId);
    return contactRepository.save(contact);
  }

  private void demoteExistingPrimary(UUID tenantId, UUID partnerId, PartnerContactRole role) {
    List<PartnerContact> primaries =
        contactRepository.findActivePrimaryByTenantIdAndPartnerIdAndRole(tenantId, partnerId, role);
    primaries.forEach(PartnerContact::demotePrimary);
    contactRepository.saveAll(primaries);
  }

  private TradingPartner requirePartner(UUID tenantId, UUID partnerId) {
    return partnerRepository
        .findByTenantIdAndId(tenantId, partnerId)
        .orElseThrow(() -> new CommonDomainException("Trading partner not found: " + partnerId));
  }
}
