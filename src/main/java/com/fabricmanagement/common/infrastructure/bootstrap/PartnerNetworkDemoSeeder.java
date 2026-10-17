package com.fabricmanagement.common.infrastructure.bootstrap;

import com.fabricmanagement.common.domain.vo.Verification;
import com.fabricmanagement.common.infrastructure.bootstrap.PartnerNetworkDemoData.CardSpec;
import com.fabricmanagement.common.infrastructure.bootstrap.PartnerNetworkDemoData.ContactSpec;
import com.fabricmanagement.common.infrastructure.bootstrap.PartnerNetworkDemoData.CustomerSpec;
import com.fabricmanagement.common.infrastructure.bootstrap.PartnerNetworkDemoData.LinkedSpec;
import com.fabricmanagement.common.infrastructure.bootstrap.PartnerNetworkDemoData.Place;
import com.fabricmanagement.common.infrastructure.bootstrap.PartnerNetworkDemoData.SupplierSpec;
import com.fabricmanagement.platform.communication.dto.CreateAddressRequest;
import com.fabricmanagement.platform.communication.infra.repository.AddressRepository;
import com.fabricmanagement.platform.organization.api.facade.OrganizationAddressFacade;
import com.fabricmanagement.platform.organization.dto.OrganizationAddressDto;
import com.fabricmanagement.platform.tradingpartner.app.PartnerContactService;
import com.fabricmanagement.platform.tradingpartner.app.PartnerLinkService;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerService;
import com.fabricmanagement.platform.tradingpartner.domain.PartnerLinkRole;
import com.fabricmanagement.platform.tradingpartner.dto.CreatePartnerContactRequest;
import com.fabricmanagement.platform.tradingpartner.dto.CreatePartnerLinkRequest;
import com.fabricmanagement.platform.tradingpartner.dto.PartnerLinkDto;
import com.fabricmanagement.platform.tradingpartner.dto.QuickCreatePartnerLinkRequest;
import com.fabricmanagement.platform.tradingpartner.dto.TradingPartnerDto;
import com.fabricmanagement.platform.tradingpartner.infra.repository.TradingPartnerRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Gives a playground tenant's customers and suppliers the people, address cards and linked
 * companies the order form reads (PLAYGROUND-PARTIES-1): the contacts to address an order to, the
 * address cards a delivery can use, and the garment makers, dyehouses and forwarders that receive
 * goods for a customer, each with its own contacts and cards.
 *
 * <p>Writes through the services the screens use, so the data obeys the same rules. Every address
 * is on a reserved {@code .example} domain; the sandbox redirects each message to the address the
 * prospect registered with (EMAIL-SANDBOX-1). Verification is set explicitly, not left to the
 * seeding thread's permissions: a few records stay unverified so the order form shows both states.
 *
 * <p>Idempotent per owner: a customer, supplier, linked company or organization that already has
 * contacts or cards is left alone. A customer or supplier the other demos did not create is
 * skipped. The caller binds the tenant in {@code TenantContext}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PartnerNetworkDemoSeeder {

  private final TradingPartnerService tradingPartnerService;
  private final TradingPartnerRepository partnerRepository;
  private final PartnerContactService contactService;
  private final PartnerLinkService linkService;
  private final OrganizationAddressFacade addressFacade;
  private final AddressRepository addressRepository;
  private final TransactionTemplate transactionTemplate;

  /**
   * Seeds contacts, cards and linked companies for {@code tenantId}, in a transaction of its own:
   * the colour/stock demo runs after the caller's commit, and the work that follows it must not
   * join a transaction that is already over.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void seedFor(UUID tenantId) {
    for (CustomerSpec customer : PartnerNetworkDemoData.CUSTOMERS) {
      seedCustomer(tenantId, customer);
    }
    for (SupplierSpec supplier : PartnerNetworkDemoData.SUPPLIERS) {
      find(tenantId, supplier.name())
          .ifPresentOrElse(
              partner -> ensureContacts(partner.getId(), List.of(supplier.contact())),
              () -> log.debug("Partner network demo: supplier {} not present", supplier.name()));
    }
  }

  private void seedCustomer(UUID tenantId, CustomerSpec spec) {
    Optional<TradingPartnerDto> found = find(tenantId, spec.name());
    if (found.isEmpty()) {
      log.debug("Partner network demo: customer {} not present, skipping", spec.name());
      return;
    }
    TradingPartnerDto customer = found.get();
    ensureContacts(customer.getId(), spec.contacts());
    ensureCards(customer.getOrganizationId(), spec.place(), spec.cards());
    for (LinkedSpec linked : spec.linked()) {
      TradingPartnerDto partner = ensureLinked(tenantId, customer, linked);
      ensureContacts(partner.getId(), linked.contacts());
      ensureCards(partner.getOrganizationId(), linked.place(), linked.cards());
    }
    log.info("Partner network demo seeded for customer {}", spec.name());
  }

  private TradingPartnerDto ensureLinked(
      UUID tenantId, TradingPartnerDto customer, LinkedSpec spec) {
    Optional<TradingPartnerDto> existing = find(tenantId, spec.name());
    if (existing.isPresent()) {
      TradingPartnerDto partner = existing.get();
      if (!isLinked(customer.getId(), partner.getId(), spec.role())) {
        CreatePartnerLinkRequest request = new CreatePartnerLinkRequest();
        request.setLinkedPartnerId(partner.getId());
        request.setRole(spec.role());
        request.setNote(spec.note());
        linkService.link(customer.getId(), request);
      }
      return partner;
    }

    QuickCreatePartnerLinkRequest request = new QuickCreatePartnerLinkRequest();
    request.setCompanyName(spec.name());
    // The shared registry matches on tax id and country across tenants; a per-tenant suffix keeps
    // each playground's company its own record.
    request.setTaxId(spec.taxPrefix() + "-" + tenantId.toString().substring(0, 8));
    request.setCountry(spec.place().alpha3());
    request.setCity(spec.cards().isEmpty() ? null : spec.cards().get(0).city());
    request.setRole(spec.role());
    request.setNote(spec.note());
    request.setCreateAnyway(true);
    TradingPartnerDto created = linkService.quickCreate(customer.getId(), request).partner();
    setPartnerVerified(tenantId, created.getId(), spec.verified());
    return created;
  }

  private boolean isLinked(UUID customerId, UUID linkedId, PartnerLinkRole role) {
    return linkService.list(customerId).stream().anyMatch(link -> matches(link, linkedId, role));
  }

  private static boolean matches(PartnerLinkDto link, UUID linkedId, PartnerLinkRole role) {
    return link.linkedPartnerId().equals(linkedId) && link.role() == role;
  }

  private void ensureContacts(UUID partnerId, List<ContactSpec> contacts) {
    if (!contactService.listContacts(partnerId, null).isEmpty()) {
      return;
    }
    for (ContactSpec spec : contacts) {
      CreatePartnerContactRequest request = new CreatePartnerContactRequest();
      request.setName(spec.name());
      request.setEmail(spec.email());
      request.setPhone(spec.phone());
      request.setRole(spec.role());
      request.setPrimary(true);
      contactService.createContact(partnerId, request);
    }
  }

  private void ensureCards(UUID organizationId, Place place, List<CardSpec> cards) {
    if (organizationId == null) {
      log.warn("Partner network demo: a partner has no organization, its cards are skipped");
      return;
    }
    if (!addressFacade.getOrganizationAddresses(organizationId).isEmpty()) {
      return;
    }
    for (CardSpec spec : cards) {
      CreateAddressRequest request =
          CreateAddressRequest.builder()
              .label(spec.label())
              .addressType(spec.type())
              .streetAddress(spec.street())
              .addressLine2(spec.line2())
              .city(spec.city())
              .state(spec.region())
              .postalCode(spec.postalCode())
              .country(place.name())
              .countryCode(place.alpha2())
              .build();
      OrganizationAddressDto card =
          addressFacade.createAndAssignAddress(
              organizationId, request, spec.primary(), spec.headquarters());
      setCardVerified(card.getAddressId(), spec.verified());
    }
  }

  /** Verified here means made by the system: confirmed, with nobody to name. */
  private void setPartnerVerified(UUID tenantId, UUID partnerId, boolean verified) {
    transactionTemplate.executeWithoutResult(
        status ->
            partnerRepository
                .findByTenantIdAndId(tenantId, partnerId)
                .ifPresent(
                    partner -> {
                      partner.setVerification(stateOf(verified));
                      partnerRepository.save(partner);
                    }));
  }

  private void setCardVerified(UUID addressId, boolean verified) {
    transactionTemplate.executeWithoutResult(
        status ->
            addressRepository
                .findById(addressId)
                .ifPresent(
                    address -> {
                      address.setVerification(stateOf(verified));
                      addressRepository.save(address);
                    }));
  }

  private static Verification stateOf(boolean verified) {
    return verified ? Verification.predating() : Verification.unverified();
  }

  private Optional<TradingPartnerDto> find(UUID tenantId, String name) {
    return tradingPartnerService.searchByName(tenantId, name).stream()
        .filter(partner -> name.equalsIgnoreCase(partner.getDisplayName()))
        .findFirst();
  }
}
