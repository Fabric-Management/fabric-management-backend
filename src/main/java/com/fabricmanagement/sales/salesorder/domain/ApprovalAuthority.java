package com.fabricmanagement.sales.salesorder.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.sales.common.exception.OrderDomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A customer representative's authority to approve orders and request changes on the customer's
 * behalf (ADR-0014 D4, OD-3c). It is granted by an authorised sales manager with its basis and
 * never inferred: receiving goods, receiving a document or giving a sharing instruction grants
 * nothing. The grant is never edited; the only change is its revocation, recorded once with who and
 * why.
 *
 * <p>The customer's contacts in the platform are contact points (an e-mail address, a phone), not
 * people. The authority therefore names the representative, binds the e-mail contact point and
 * keeps the address that contact point read when it was granted. The approval link and one-time
 * code go only to that address; holding that mailbox is what the code proves. A contact point can
 * be edited on the customer's card, so an edit must not carry the authority to another mailbox: the
 * change of the contact point ends the authority permanently, recorded like a revocation with its
 * cause. Changing the address back does not revive it; an authorised user grants a new authority at
 * whichever address is confirmed then (ADR-0014 OD-13).
 */
@Entity
@Table(name = "customer_approval_authority", schema = "sales_ord")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApprovalAuthority extends BaseEntity {

  public static final int MAX_REPRESENTATIVE_NAME = 200;
  public static final int MAX_EMAIL = 255;
  public static final int MAX_BASIS_REFERENCE = 500;
  public static final int MAX_REVOCATION_REASON = 500;
  private static final String EMAIL_FORM = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";

  @Column(name = "trading_partner_id", nullable = false, updatable = false)
  private UUID tradingPartnerId;

  /** The person who holds the authority, as named in its basis. */
  @Column(
      name = "representative_name",
      nullable = false,
      updatable = false,
      length = MAX_REPRESENTATIVE_NAME)
  private String representativeName;

  /** The representative's e-mail contact point among the customer's contacts. */
  @Column(name = "contact_id", nullable = false, updatable = false)
  private UUID contactId;

  /** The address the contact point read when the authority was granted; requests go only here. */
  @Column(name = "authorised_email", nullable = false, updatable = false, length = MAX_EMAIL)
  private String authorisedEmail;

  @Enumerated(EnumType.STRING)
  @Column(name = "basis", nullable = false, updatable = false, length = 30)
  private ApprovalAuthorityBasis basis;

  /** The mandate, letter, clause or register entry the authority rests on. */
  @Column(
      name = "basis_reference",
      nullable = false,
      updatable = false,
      length = MAX_BASIS_REFERENCE)
  private String basisReference;

  @Column(name = "valid_from", nullable = false, updatable = false)
  private LocalDate validFrom;

  /** Last day of validity; open-ended when null. */
  @Column(name = "valid_until", updatable = false)
  private LocalDate validUntil;

  @Column(name = "granted_by", nullable = false, updatable = false)
  private UUID grantedBy;

  @Column(name = "granted_at", nullable = false, updatable = false)
  private Instant grantedAt;

  @Column(name = "revoked_by")
  private UUID revokedBy;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Column(name = "revocation_reason", length = MAX_REVOCATION_REASON)
  private String revocationReason;

  /** Why the authority ended: revoked by a user, or its contact point changed or was removed. */
  @Enumerated(EnumType.STRING)
  @Column(name = "revocation_cause", length = 30)
  private ApprovalAuthorityEnd revocationCause;

  public static ApprovalAuthority grant(
      UUID tradingPartnerId,
      String representativeName,
      UUID contactId,
      String authorisedEmail,
      ApprovalAuthorityBasis basis,
      String basisReference,
      LocalDate validFrom,
      LocalDate validUntil,
      UUID grantedBy,
      Instant grantedAt) {
    if (tradingPartnerId == null || contactId == null || grantedBy == null || grantedAt == null) {
      throw new IllegalArgumentException("Customer, contact, grantor and time are required");
    }
    String representative = Text.trimmed(representativeName);
    if (representative == null) {
      throw new OrderDomainException("Name the representative who holds the authority");
    }
    String email = Text.trimmed(authorisedEmail);
    if (email == null || email.length() > MAX_EMAIL || !email.matches(EMAIL_FORM)) {
      throw OrderDomainException.rule(
          "APPROVER_CONTACT_NOT_EMAIL",
          "The representative's contact point needs a valid e-mail address");
    }
    String reference = Text.trimmed(basisReference);
    if (basis == null || reference == null) {
      throw new OrderDomainException("Record what the approval authority rests on");
    }
    if (validFrom == null) {
      throw new OrderDomainException("Enter the first day the authority is valid");
    }
    if (validUntil != null && validUntil.isBefore(validFrom)) {
      throw new OrderDomainException("The authority cannot end before it starts");
    }
    ApprovalAuthority value = new ApprovalAuthority();
    value.tradingPartnerId = tradingPartnerId;
    value.representativeName =
        Text.limited(
            representative, MAX_REPRESENTATIVE_NAME, "The representative's name is too long");
    value.contactId = contactId;
    value.authorisedEmail = email;
    value.basis = basis;
    value.basisReference =
        Text.limited(reference, MAX_BASIS_REFERENCE, "The basis reference is too long");
    value.validFrom = validFrom;
    value.validUntil = validUntil;
    value.grantedBy = grantedBy;
    value.grantedAt = grantedAt;
    return value;
  }

  /** Ends the authority; recorded once, with who and why. */
  public void revoke(UUID actor, String reason, Instant at) {
    if (actor == null || at == null) {
      throw new IllegalArgumentException("Actor and time are required");
    }
    if (revokedAt != null) {
      throw OrderDomainException.stage(
          "APPROVAL_AUTHORITY_REVOKED", "This approval authority was already revoked");
    }
    String why = Text.trimmed(reason);
    if (why == null) {
      throw new OrderDomainException("Say why the approval authority is revoked");
    }
    end(ApprovalAuthorityEnd.REVOKED, actor, why, at);
  }

  /**
   * Ends the authority because its contact point changed or was removed from the customer's card.
   * Permanent: a later change back to the authorised address does not revive it. An authority that
   * has already ended keeps its first ending.
   */
  public void endForContactChange(ApprovalAuthorityEnd cause, UUID actor, Instant at) {
    if (cause == null || !cause.isContactChange() || actor == null || at == null) {
      throw new IllegalArgumentException("A contact change, actor and time are required");
    }
    if (revokedAt != null) {
      return;
    }
    end(
        cause,
        actor,
        cause == ApprovalAuthorityEnd.CONTACT_REMOVED
            ? "The representative's e-mail contact point was removed from the customer's card"
            : "The representative's e-mail contact point was changed on the customer's card",
        at);
  }

  private void end(ApprovalAuthorityEnd cause, UUID actor, String reason, Instant at) {
    this.revocationCause = cause;
    this.revocationReason = Text.limited(reason, MAX_REVOCATION_REASON, "The reason is too long");
    this.revokedBy = actor;
    this.revokedAt = at;
  }

  /** Whether the authority ended because its contact point changed or was removed. */
  public boolean endedByContactChange() {
    return revokedAt != null && revocationCause != null && revocationCause.isContactChange();
  }

  /**
   * Whether the contact point, reading {@code currentAddress} now, still is the mailbox the
   * authority was granted for. Case and surrounding blanks do not count; anything else does, and a
   * contact point removed from the card ({@code null}) reaches nobody.
   */
  public boolean isAddressedAt(String currentAddress) {
    String current = Text.trimmed(currentAddress);
    return current != null && current.equalsIgnoreCase(authorisedEmail);
  }

  public boolean isRevoked() {
    return revokedAt != null;
  }

  /** Whether the contact may approve on {@code day}: not revoked and within its validity. */
  public boolean isActiveOn(LocalDate day) {
    return revokedAt == null
        && !day.isBefore(validFrom)
        && (validUntil == null || !day.isAfter(validUntil));
  }

  @Override
  protected String getModuleCode() {
    return "SAA";
  }
}
