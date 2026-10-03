package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.platform.communication.domain.ContactType;
import com.fabricmanagement.platform.organization.api.facade.OrganizationContactFacade;
import com.fabricmanagement.platform.organization.dto.OrganizationContactDto;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerService;
import com.fabricmanagement.platform.tradingpartner.dto.TradingPartnerDto;
import com.fabricmanagement.sales.common.exception.OrderDomainException;
import com.fabricmanagement.sales.salesorder.domain.ApprovalAuthority;
import com.fabricmanagement.sales.salesorder.domain.CustomerApproval;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.infra.repository.ApprovalAuthorityRepository;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Who may approve for the customer, in force (ADR-0014 D4, OD-13). A request for the customer's
 * approval goes to the order's designated approver under a valid authority, at the address that
 * authority was granted for. Afterwards every step of the customer — a code, its check, the
 * content, a decision — needs that very authority to be in force still:
 *
 * <ul>
 *   <li>not ended and within its validity. A change of its contact point on the customer's card
 *       ends it permanently, in the transaction that made the change ({@code
 *       ApprovalAuthorityContactListener}); changing the address back does not revive it, and the
 *       new address needs a new grant by an authorised user;
 *   <li>its contact point still reading the address it was granted for — a second guard for a
 *       change that bypassed the card's services.
 * </ul>
 *
 * A later grant to the same person is another authority and never revives an earlier request. Steps
 * that send or decide hold the authority's row lock ({@link #holdValid}); revoking takes the same
 * lock, so a revocation and a decision never overlap.
 */
@Component
@RequiredArgsConstructor
public class ApproverAuthorities {

  public static final String APPROVER_REQUIRED = "APPROVER_REQUIRED";
  public static final String AUTHORITY_INACTIVE = "APPROVAL_AUTHORITY_INACTIVE";
  public static final String APPROVER_EMAIL_CHANGED = "APPROVER_EMAIL_CHANGED";

  private final ApprovalAuthorityRepository authorities;
  private final TradingPartnerService partners;
  private final OrganizationContactFacade contacts;
  private final EntityManager entityManager;
  private final Clock clock;

  /** The representative a request goes to, under which authority, at which address. */
  public record Approver(UUID authorityId, String name, String email) {}

  /** The approver for a new request, or the reason there is none ({@code block}). */
  public record Resolution(Approver approver, String block) {
    static Resolution blocked(String reason) {
      return new Resolution(null, reason);
    }
  }

  /** The order's designated approver as a request would be sent now. */
  public Resolution resolve(SalesOrder order) {
    if (order.getApproverContactId() == null) {
      return Resolution.blocked(APPROVER_REQUIRED);
    }
    UUID tenantId = TenantContext.requireTenantId();
    LocalDate today = LocalDate.ofInstant(clock.instant(), clock.getZone());
    Optional<ApprovalAuthority> found =
        authorities
            .findByTenantIdAndTradingPartnerIdAndContactIdAndRevokedAtIsNull(
                tenantId, order.getTradingPartnerId(), order.getApproverContactId())
            .filter(value -> value.isActiveOn(today));
    if (found.isEmpty()) {
      // No open authority: say so precisely when the last one ended with its contact point.
      boolean contactChanged =
          authorities
              .findFirstByTenantIdAndTradingPartnerIdAndContactIdOrderByGrantedAtDesc(
                  tenantId, order.getTradingPartnerId(), order.getApproverContactId())
              .map(ApprovalAuthority::endedByContactChange)
              .orElse(false);
      return Resolution.blocked(contactChanged ? APPROVER_EMAIL_CHANGED : AUTHORITY_INACTIVE);
    }
    ApprovalAuthority authority = found.get();
    if (!addressHolds(tenantId, authority)) {
      return Resolution.blocked(APPROVER_EMAIL_CHANGED);
    }
    return new Resolution(
        new Approver(
            authority.getId(), authority.getRepresentativeName(), authority.getAuthorisedEmail()),
        null);
  }

  /**
   * Why the authority {@code approval} was sent under is not in force at {@code now}, or null when
   * it is. Reads without a lock: for showing a state and for steps that neither send nor decide.
   */
  public String block(CustomerApproval approval, Instant now) {
    return blockOf(authorityOf(approval, false), now);
  }

  /** Whether the authority {@code approval} was sent under is in force at {@code now}. */
  public boolean isValid(CustomerApproval approval, Instant now) {
    return block(approval, now) == null;
  }

  /**
   * Refuses once the request's authority is no longer in force, without a lock: only for read-only
   * transactions. Every step of the customer and every send uses {@link #holdValid}.
   */
  public void requireValid(CustomerApproval approval, Instant now) {
    String reason = block(approval, now);
    if (reason != null) {
      throw refusal(reason);
    }
  }

  /**
   * Locks the request's authority until the transaction ends and refuses unless it is in force.
   * Every send (link, resend, code) and every step of the customer (code check, content, decision)
   * calls this inside its read-write transaction: an ending committed before — a revocation, or a
   * change of the contact point — is seen here, and one started after waits until this step has
   * committed.
   */
  public void holdValid(CustomerApproval approval, Instant now) {
    String reason = blockOf(authorityOf(approval, true), now);
    if (reason != null) {
      throw refusal(reason);
    }
  }

  /** The refusal for a reason {@link #block} returned. */
  public static OrderDomainException refusal(String reason) {
    return switch (reason) {
      case APPROVER_EMAIL_CHANGED ->
          OrderDomainException.stage(
              APPROVER_EMAIL_CHANGED,
              "The representative's e-mail contact point changed on the customer's card, which"
                  + " ended the approval authority for good; a new authority has to be granted at"
                  + " the confirmed address and a new request sent");
      default ->
          OrderDomainException.stage(
              AUTHORITY_INACTIVE,
              "The representative's authority to approve for the customer has ended; the seller"
                  + " has to send a new request");
    };
  }

  private String blockOf(Optional<ApprovalAuthority> found, Instant now) {
    if (found.isEmpty()) {
      return AUTHORITY_INACTIVE;
    }
    if (found.get().endedByContactChange()) {
      return APPROVER_EMAIL_CHANGED;
    }
    if (!found.get().isActiveOn(LocalDate.ofInstant(now, clock.getZone()))) {
      return AUTHORITY_INACTIVE;
    }
    return addressHolds(TenantContext.requireTenantId(), found.get())
        ? null
        : APPROVER_EMAIL_CHANGED;
  }

  private Optional<ApprovalAuthority> authorityOf(CustomerApproval approval, boolean lock) {
    if (approval.getApprovalAuthorityId() == null) {
      return Optional.empty();
    }
    UUID tenantId = TenantContext.requireTenantId();
    if (!lock) {
      return authorities.findByTenantIdAndId(tenantId, approval.getApprovalAuthorityId());
    }
    Optional<ApprovalAuthority> locked =
        authorities.lockByTenantIdAndId(tenantId, approval.getApprovalAuthorityId());
    // An instance read earlier in this transaction keeps the state it was read with; under the
    // lock it is read again, so a revocation committed meanwhile is seen.
    locked.ifPresent(entityManager::refresh);
    return locked;
  }

  /** Whether the authority's contact point still reads the address it was granted for. */
  private boolean addressHolds(UUID tenantId, ApprovalAuthority authority) {
    return authority.isAddressedAt(
        emailOf(tenantId, authority.getTradingPartnerId(), authority.getContactId()));
  }

  private String emailOf(UUID tenantId, UUID customerId, UUID contactId) {
    UUID organizationId =
        partners
            .findById(tenantId, customerId)
            .map(TradingPartnerDto::getOrganizationId)
            .orElse(null);
    if (organizationId == null) {
      return null;
    }
    return contacts.getOrganizationContacts(organizationId).stream()
        .filter(value -> contactId.equals(value.getContactId()))
        .map(OrganizationContactDto::getContact)
        .filter(value -> value != null && value.getContactType() == ContactType.EMAIL)
        .map(OrganizationContactDto.ContactData::getContactValue)
        .filter(value -> value != null && !value.isBlank())
        .map(String::trim)
        .findFirst()
        .orElse(null);
  }
}
