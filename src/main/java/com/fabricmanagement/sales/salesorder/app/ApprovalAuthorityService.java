package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.platform.communication.domain.ContactType;
import com.fabricmanagement.platform.organization.api.facade.OrganizationContactFacade;
import com.fabricmanagement.platform.organization.dto.OrganizationContactDto;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerService;
import com.fabricmanagement.platform.tradingpartner.dto.TradingPartnerDto;
import com.fabricmanagement.platform.user.domain.SystemUser;
import com.fabricmanagement.sales.common.exception.OrderDomainException;
import com.fabricmanagement.sales.salesorder.domain.ApprovalAuthority;
import com.fabricmanagement.sales.salesorder.domain.ApprovalAuthorityEnd;
import com.fabricmanagement.sales.salesorder.dto.OrderPartyDtos.ApprovalAuthorityView;
import com.fabricmanagement.sales.salesorder.dto.OrderPartyDtos.GrantApprovalAuthorityRequest;
import com.fabricmanagement.sales.salesorder.dto.OrderPartyDtos.RevokeApprovalAuthorityRequest;
import com.fabricmanagement.sales.salesorder.infra.repository.ApprovalAuthorityRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Who may approve orders for a customer (ADR-0014 D4, OD-3c). Granting and revoking need the
 * explicitly distributed permission {@code sales:grant-approval-authority}, checked at the
 * endpoint; the grant names its basis and is kept with its revocation for the record.
 *
 * <p>A grant keeps the address its e-mail contact point reads at that moment (ADR-0014 OD-13). A
 * later change of that contact point ends the authority permanently, in the same transaction as the
 * change ({@link #endForContactChange}); changing it back does not revive it. Confirming that the
 * new address is the same representative's is again an authorised act: a new grant at that address.
 * The view says why an authority ended and whether its contact point still reads the authorised
 * address.
 */
@Service
@RequiredArgsConstructor
public class ApprovalAuthorityService {

  private final ApprovalAuthorityRepository authorities;
  private final TradingPartnerService partners;
  private final OrganizationContactFacade contacts;
  private final Clock clock;

  @Transactional(readOnly = true)
  public List<ApprovalAuthorityView> list(UUID customerId) {
    UUID tenantId = TenantContext.requireTenantId();
    TradingPartnerDto customer = customer(tenantId, customerId);
    LocalDate today = LocalDate.now(clock);
    Map<UUID, String> addresses = emailAddresses(customer);
    return authorities
        .findByTenantIdAndTradingPartnerIdOrderByGrantedAtDesc(tenantId, customerId)
        .stream()
        .map(value -> view(value, today, addresses))
        .toList();
  }

  /**
   * Grants the authority to one of the customer's contacts, at the address the grantor confirms. A
   * contact holds at most one authority that is not revoked; to change its basis, validity or
   * address the current one is revoked first, so the record shows what applied when.
   */
  @Transactional
  public ApprovalAuthorityView grant(
      UUID customerId, GrantApprovalAuthorityRequest request, UUID actor) {
    UUID tenantId = TenantContext.requireTenantId();
    TradingPartnerDto customer = customer(tenantId, customerId);
    OrganizationContactDto contact =
        customer.getOrganizationId() == null
            ? null
            : contacts.getOrganizationContacts(customer.getOrganizationId()).stream()
                .filter(value -> request.contactId().equals(value.getContactId()))
                .findFirst()
                .orElse(null);
    if (contact == null) {
      throw OrderDomainException.rule(
          "CONTACT_NOT_OF_CUSTOMER", "That contact is not one of the customer's contacts");
    }
    // The approval link and one-time code go to this contact point: it must be an e-mail address.
    if (contact.getContact() == null
        || contact.getContact().getContactType() != ContactType.EMAIL) {
      throw OrderDomainException.rule(
          "APPROVER_CONTACT_NOT_EMAIL",
          "Choose the representative's e-mail address: the approval code is sent there");
    }
    // The grantor confirms the address; the contact point must read that address now, so an edit
    // made meanwhile is never granted unseen.
    String reads = contact.getContact().getContactValue();
    if (reads == null
        || request.email() == null
        || !reads.trim().equalsIgnoreCase(request.email().trim())) {
      throw OrderDomainException.stage(
          ApproverAuthorities.APPROVER_EMAIL_CHANGED,
          "The contact point does not read the address you confirmed; check the customer's card");
    }
    if (authorities
        .findByTenantIdAndTradingPartnerIdAndContactIdAndRevokedAtIsNull(
            tenantId, customerId, request.contactId())
        .isPresent()) {
      throw OrderDomainException.stage(
          "APPROVAL_AUTHORITY_EXISTS",
          "This contact already holds an approval authority: revoke it before granting a new one");
    }
    ApprovalAuthority saved =
        authorities.saveAndFlush(
            ApprovalAuthority.grant(
                customerId,
                request.representativeName(),
                request.contactId(),
                // The confirmed address, as the contact point reads it now.
                reads,
                request.basis(),
                request.basisReference(),
                request.validFrom(),
                request.validUntil(),
                actor,
                clock.instant()));
    return view(saved, LocalDate.now(clock), emailAddresses(customer));
  }

  /**
   * Revokes an authority. Orders that designated the contact keep the designation, but no request
   * can be sent under it and no request sent under it can be decided any more.
   *
   * <p>The authority's row is locked first, the same lock a decision holds while it checks the
   * authority and records the decision: a decision in progress finishes before the revocation, and
   * a decision that comes after the revocation committed is refused (ADR-0014 OD-13).
   */
  @Transactional
  public ApprovalAuthorityView revoke(
      UUID customerId, UUID authorityId, RevokeApprovalAuthorityRequest request, UUID actor) {
    UUID tenantId = TenantContext.requireTenantId();
    TradingPartnerDto customer = customer(tenantId, customerId);
    ApprovalAuthority authority =
        authorities
            .lockByTenantIdAndId(tenantId, authorityId)
            .filter(value -> customerId.equals(value.getTradingPartnerId()))
            .orElseThrow(
                () -> new NotFoundException("Approval authority not found: " + authorityId));
    authority.revoke(actor, request.reason(), clock.instant());
    authorities.flush();
    return view(authority, LocalDate.now(clock), emailAddresses(customer));
  }

  /**
   * The customer's card changed the contact point {@code contactId}: its address (or type) changed,
   * it was deleted, or it was removed from the organization {@code organizationId} (null: from
   * every organization). Each open authority bound to it ends now, permanently, under its row lock
   * — the lock every send and decision holds — so nothing is sent or decided under it after this
   * transaction commits, and a send or decision in progress finishes first.
   *
   * @return how many authorities ended
   */
  @Transactional
  public int endForContactChange(UUID contactId, UUID organizationId, ApprovalAuthorityEnd cause) {
    UUID tenantId = TenantContext.requireTenantId();
    int ended = 0;
    for (ApprovalAuthority authority :
        authorities.lockOpenByTenantIdAndContactId(tenantId, contactId)) {
      if (organizationId != null
          && !organizationId.equals(
              partners
                  .findById(tenantId, authority.getTradingPartnerId())
                  .map(TradingPartnerDto::getOrganizationId)
                  .orElse(null))) {
        continue;
      }
      authority.endForContactChange(cause, SystemUser.ID, clock.instant());
      ended++;
    }
    authorities.flush();
    return ended;
  }

  private static ApprovalAuthorityView view(
      ApprovalAuthority value, LocalDate today, Map<UUID, String> addresses) {
    return ApprovalAuthorityView.of(
        value, today, !value.isAddressedAt(addresses.get(value.getContactId())));
  }

  /** The e-mail address each of the customer's e-mail contact points reads now. */
  private Map<UUID, String> emailAddresses(TradingPartnerDto customer) {
    Map<UUID, String> addresses = new HashMap<>();
    if (customer.getOrganizationId() == null) {
      return addresses;
    }
    for (OrganizationContactDto value :
        contacts.getOrganizationContacts(customer.getOrganizationId())) {
      if (value.getContact() != null
          && value.getContact().getContactType() == ContactType.EMAIL
          && value.getContactId() != null) {
        addresses.put(value.getContactId(), value.getContact().getContactValue());
      }
    }
    return addresses;
  }

  private TradingPartnerDto customer(UUID tenantId, UUID customerId) {
    return partners
        .findById(tenantId, customerId)
        .orElseThrow(() -> new NotFoundException("Customer not found: " + customerId));
  }
}
