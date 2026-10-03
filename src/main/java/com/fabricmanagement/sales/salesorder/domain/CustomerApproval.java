package com.fabricmanagement.sales.salesorder.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.sales.common.exception.OrderDomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One request for the customer's approval of a sent {@link OrderVersion}, to the representative
 * holding the approval authority it names, at the address that authority was granted for. Today the
 * e-mailed link with its one-time code is the only door; a customer account will lead here too once
 * the signed-in person is matched to the authority (ADR-0014 OD-13). Only hashes of the link, the
 * code and the verified session are kept.
 */
@Entity
@Table(name = "customer_approval", schema = "sales_ord")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerApproval extends BaseEntity {

  public static final int DEFAULT_LINK_HOURS = 48;
  public static final int MAX_LINK_HOURS = 168;
  public static final Duration CODE_TTL = Duration.ofMinutes(10);
  public static final Duration CODE_COOLDOWN = Duration.ofSeconds(60);
  public static final Duration SESSION_TTL = Duration.ofMinutes(30);
  public static final int MAX_CODES = 5;
  public static final int MAX_CODE_ATTEMPTS = 5;
  public static final int MAX_NOTE_LENGTH = 2000;
  public static final int MAX_REASON_LENGTH = 1000;

  @Column(name = "sales_order_id", nullable = false, updatable = false)
  private UUID salesOrderId;

  @Column(name = "order_version_id", nullable = false, updatable = false)
  private UUID orderVersionId;

  @Column(name = "version_no", nullable = false, updatable = false)
  private int versionNo;

  /**
   * The representative's approval authority the request was sent under (ADR-0014 D4). The link, the
   * code and a decision work only while this very authority is valid; a later grant to the same
   * person never revives a request sent under one that ended.
   */
  @Column(name = "approval_authority_id", nullable = false, updatable = false)
  private UUID approvalAuthorityId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 30)
  private CustomerApprovalStatus status;

  @Column(name = "recipient_name", length = 200, updatable = false)
  private String recipientName;

  /** The address the request's authority was granted for; it never changes (ADR-0014 OD-13). */
  @Column(name = "recipient_email", nullable = false, updatable = false, length = 255)
  private String recipientEmail;

  /** Planning's proposal ends here; no link outlives it. */
  @Column(name = "proposal_valid_until", nullable = false, updatable = false)
  private Instant proposalValidUntil;

  @Column(name = "link_valid_hours", nullable = false, updatable = false)
  private int linkValidHours;

  @Column(name = "requested_by", nullable = false, updatable = false)
  private UUID requestedBy;

  @Column(name = "requested_at", nullable = false, updatable = false)
  private Instant requestedAt;

  /** The tenant's approval request the version waits for; null when the policy needed none. */
  @Column(name = "internal_approval_request_id")
  private UUID internalApprovalRequestId;

  @Column(name = "internal_decided_at")
  private Instant internalDecidedAt;

  @Column(name = "internal_rejection_reason", length = MAX_REASON_LENGTH)
  private String internalRejectionReason;

  @Column(name = "token_hash", length = 64)
  private String tokenHash;

  @Column(name = "link_expires_at")
  private Instant linkExpiresAt;

  @Column(name = "sent_at")
  private Instant sentAt;

  @Column(name = "sent_by")
  private UUID sentBy;

  /** How many links were issued for it; a resend replaces the earlier link. */
  @Column(name = "links_issued", nullable = false)
  private int linksIssued;

  @Column(name = "code_hash", length = 64)
  private String codeHash;

  @Column(name = "code_sent_at")
  private Instant codeSentAt;

  @Column(name = "code_expires_at")
  private Instant codeExpiresAt;

  @Column(name = "codes_sent", nullable = false)
  private int codesSent;

  @Column(name = "code_attempts", nullable = false)
  private int codeAttempts;

  @Column(name = "session_hash", length = 64)
  private String sessionHash;

  @Column(name = "session_expires_at")
  private Instant sessionExpiresAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "decision_channel", length = 20)
  private CustomerApprovalChannel decisionChannel;

  @Column(name = "decided_at")
  private Instant decidedAt;

  @Column(name = "decided_by_name", length = 200)
  private String decidedByName;

  @Column(name = "decided_by_email", length = 255)
  private String decidedByEmail;

  @Column(name = "decided_by_user_id")
  private UUID decidedByUserId;

  @Column(name = "customer_note", length = MAX_NOTE_LENGTH)
  private String customerNote;

  /** Why an approval could not be fulfilled: what the re-check found. */
  @Column(name = "decision_detail", length = MAX_REASON_LENGTH)
  private String decisionDetail;

  @Column(name = "ip_address", length = 64)
  private String ipAddress;

  @Column(name = "user_agent", length = 500)
  private String userAgent;

  @Column(name = "closed_reason", length = MAX_REASON_LENGTH)
  private String closedReason;

  @Column(name = "closed_at")
  private Instant closedAt;

  @Column(name = "closed_by")
  private UUID closedBy;

  /** When sales followed up the customer's change request (a new version sent, or cancelled). */
  @Column(name = "changes_resolved_at")
  private Instant changesResolvedAt;

  /** Who decided and how; the representative's identity as verified by the channel. */
  public record Decider(
      CustomerApprovalChannel channel,
      String name,
      String email,
      UUID userId,
      String ipAddress,
      String userAgent) {

    public Decider {
      Objects.requireNonNull(channel, "channel");
    }
  }

  /**
   * Asks the customer's representative holding {@code approvalAuthorityId} to approve {@code
   * version}, at the address that authority was granted for. It waits for the internal approval or
   * the link, whichever comes next; the link lasts {@code linkValidHours} but never beyond the
   * proposal's validity.
   */
  public static CustomerApproval request(
      OrderVersion version,
      UUID approvalAuthorityId,
      String recipientName,
      String recipientEmail,
      Instant proposalValidUntil,
      Integer linkValidHours,
      UUID requestedBy,
      Instant now) {
    if (version == null || version.getKind() != OrderVersionKind.APPROVAL) {
      throw new IllegalArgumentException("Only a version for approval is approved");
    }
    if (requestedBy == null || now == null || proposalValidUntil == null) {
      throw new IllegalArgumentException("Actor, time and the proposal's validity are required");
    }
    if (approvalAuthorityId == null) {
      throw new IllegalArgumentException("The approver's authority is required");
    }
    int hours = linkValidHours == null ? DEFAULT_LINK_HOURS : linkValidHours;
    if (hours < 1 || hours > MAX_LINK_HOURS) {
      throw new OrderDomainException(
          "The approval link lasts between 1 and " + MAX_LINK_HOURS + " hours");
    }
    if (!proposalValidUntil.isAfter(now)) {
      throw proposalExpired();
    }
    CustomerApproval value = new CustomerApproval();
    value.salesOrderId = version.getSalesOrderId();
    value.orderVersionId = version.getId();
    value.versionNo = version.getVersionNo();
    value.approvalAuthorityId = approvalAuthorityId;
    value.status = CustomerApprovalStatus.AWAITING_INTERNAL_APPROVAL;
    value.recipientName = trimmed(recipientName);
    value.recipientEmail = requireEmail(recipientEmail);
    value.proposalValidUntil = proposalValidUntil;
    value.linkValidHours = hours;
    value.requestedBy = requestedBy;
    value.requestedAt = now;
    return value;
  }

  /** The tenant's approval policy asked for this approval request before anything is sent. */
  public void awaitInternalApproval(UUID approvalRequestId) {
    requireStatus(CustomerApprovalStatus.AWAITING_INTERNAL_APPROVAL);
    if (approvalRequestId == null) {
      throw new IllegalArgumentException("The approval request is required");
    }
    this.internalApprovalRequestId = approvalRequestId;
  }

  public boolean awaitsInternalApproval() {
    return status == CustomerApprovalStatus.AWAITING_INTERNAL_APPROVAL
        && internalApprovalRequestId != null;
  }

  /** The manager or finance approved it; the link can now go to the customer. */
  public void internallyApproved(Instant now) {
    if (!awaitsInternalApproval()) {
      throw OrderDomainException.stage(
          "NOT_AWAITING_INTERNAL_APPROVAL", "The version is not waiting for an internal approval");
    }
    this.internalDecidedAt = now;
  }

  /** The manager or finance declined it; the version is never sent. */
  public void internallyRejected(String reason, Instant now) {
    if (!awaitsInternalApproval()) {
      throw OrderDomainException.stage(
          "NOT_AWAITING_INTERNAL_APPROVAL", "The version is not waiting for an internal approval");
    }
    this.status = CustomerApprovalStatus.INTERNAL_REJECTED;
    this.internalDecidedAt = now;
    this.internalRejectionReason = clip(trimmed(reason), MAX_REASON_LENGTH);
    this.closedAt = now;
  }

  /**
   * Issues a new link (the first, or a resend that replaces the earlier one). An earlier code or
   * verified session no longer counts. The link goes to the representative at the address their
   * authority was granted for, fixed when the request was made: nobody else and no other address
   * can be named here.
   */
  public void issueLink(String tokenHash, UUID actor, Instant now) {
    boolean first = status == CustomerApprovalStatus.AWAITING_INTERNAL_APPROVAL;
    if (!first && status != CustomerApprovalStatus.SENT) {
      throw OrderDomainException.stage(
          "APPROVAL_CLOSED", "This approval request is " + status + "; send a new version");
    }
    if (first && internalApprovalRequestId != null && internalDecidedAt == null) {
      throw OrderDomainException.stage(
          "AWAITING_INTERNAL_APPROVAL", "The version still waits for the internal approval");
    }
    if (!proposalValidUntil.isAfter(now)) {
      throw proposalExpired();
    }
    if (tokenHash == null || tokenHash.length() != 64) {
      throw new IllegalArgumentException("A link hash is required");
    }
    this.status = CustomerApprovalStatus.SENT;
    this.tokenHash = tokenHash;
    Instant byHours = now.plus(Duration.ofHours(linkValidHours));
    this.linkExpiresAt = byHours.isBefore(proposalValidUntil) ? byHours : proposalValidUntil;
    this.sentAt = now;
    this.sentBy = actor;
    this.linksIssued = linksIssued + 1;
    clearCode();
  }

  /** The link can be used: sent, not decided, not withdrawn and not expired. */
  public boolean isOpenAt(Instant now) {
    return status == CustomerApprovalStatus.SENT
        && linkExpiresAt != null
        && linkExpiresAt.isAfter(now);
  }

  /** Rejects a step on a link that is no longer open, saying why. */
  public void requireOpen(Instant now) {
    if (status != CustomerApprovalStatus.SENT) {
      throw OrderDomainException.stage("APPROVAL_CLOSED", "This approval request is " + status);
    }
    if (!isOpenAt(now)) {
      throw OrderDomainException.stage("LINK_EXPIRED", "This approval link has expired");
    }
  }

  /** A new one-time code goes to the contact; earlier codes stop working. */
  public void codeIssued(String codeHash, Instant now) {
    requireOpen(now);
    if (codeSentAt != null && codeSentAt.plus(CODE_COOLDOWN).isAfter(now)) {
      throw OrderDomainException.stage(
          "CODE_RECENTLY_SENT", "A code was sent a moment ago; wait before asking for another");
    }
    if (codesSent >= MAX_CODES) {
      throw OrderDomainException.stage(
          "TOO_MANY_CODES", "Too many codes were sent for this link; ask for a new link");
    }
    this.codeHash = codeHash;
    this.codeSentAt = now;
    Instant byCode = now.plus(CODE_TTL);
    this.codeExpiresAt = byCode.isBefore(linkExpiresAt) ? byCode : linkExpiresAt;
    this.codesSent = codesSent + 1;
    this.codeAttempts = 0;
  }

  /** When the next code may be asked for, or null when no code was sent yet. */
  public Instant nextCodeAllowedAt() {
    return codeSentAt == null ? null : codeSentAt.plus(CODE_COOLDOWN);
  }

  /**
   * Checks a code the representative entered. A wrong code counts towards the limit and the code
   * stops working when it is reached; a right one starts a verified session.
   *
   * @return whether the code was right
   */
  public boolean verifyCode(String enteredHash, String newSessionHash, Instant now) {
    requireOpen(now);
    if (codeHash == null || codeExpiresAt == null || !codeExpiresAt.isAfter(now)) {
      throw OrderDomainException.stage("CODE_EXPIRED", "The code has expired; ask for a new one");
    }
    if (codeAttempts >= MAX_CODE_ATTEMPTS) {
      throw OrderDomainException.stage("CODE_LOCKED", "Too many wrong codes; ask for a new code");
    }
    if (!codeHash.equals(enteredHash)) {
      this.codeAttempts = codeAttempts + 1;
      return false;
    }
    this.codeHash = null;
    this.codeExpiresAt = null;
    this.codeAttempts = 0;
    this.sessionHash = newSessionHash;
    Instant bySession = now.plus(SESSION_TTL);
    this.sessionExpiresAt = bySession.isBefore(linkExpiresAt) ? bySession : linkExpiresAt;
    return true;
  }

  public int codeAttemptsLeft() {
    return Math.max(0, MAX_CODE_ATTEMPTS - codeAttempts);
  }

  /** The session the representative verified with the code is still valid. */
  public boolean hasSession(String presentedHash, Instant now) {
    return presentedHash != null
        && sessionHash != null
        && sessionHash.equals(presentedHash)
        && sessionExpiresAt != null
        && sessionExpiresAt.isAfter(now);
  }

  /** The customer approved the version and the order was confirmed. */
  public void approved(Decider decider, Instant now) {
    decide(CustomerApprovalStatus.APPROVED, decider, null, now);
  }

  /** The customer approved, but the re-check found the approved terms can no longer be met. */
  public void approvedNotFulfillable(Decider decider, String detail, Instant now) {
    decide(CustomerApprovalStatus.APPROVED_NOT_FULFILLABLE, decider, null, now);
    this.decisionDetail = clip(trimmed(detail), MAX_REASON_LENGTH);
  }

  /** The customer asked for changes; the note says what. */
  public void changesRequested(Decider decider, String note, Instant now) {
    String text = trimmed(note);
    if (text == null) {
      throw new OrderDomainException("Say what should change");
    }
    if (text.length() > MAX_NOTE_LENGTH) {
      throw new OrderDomainException("The note is too long");
    }
    decide(CustomerApprovalStatus.CHANGES_REQUESTED, decider, text, now);
  }

  /** Sales followed up the change request: a new version went out, or the order was cancelled. */
  public void changesResolved(Instant now) {
    if (status == CustomerApprovalStatus.CHANGES_REQUESTED && changesResolvedAt == null) {
      this.changesResolvedAt = now;
    }
  }

  public boolean hasOpenChangeRequest() {
    return status == CustomerApprovalStatus.CHANGES_REQUESTED && changesResolvedAt == null;
  }

  /**
   * Takes the request back before a decision: the order changed, the evaluation was reopened, the
   * order was cancelled, or a newer version replaced it. The link stops working at once.
   */
  public void withdraw(String reason, UUID actor, Instant now) {
    if (status.isClosed()) {
      return;
    }
    this.status = CustomerApprovalStatus.WITHDRAWN;
    this.closedReason = clip(trimmed(reason), MAX_REASON_LENGTH);
    this.closedAt = now;
    this.closedBy = actor;
    clearCode();
  }

  private void decide(CustomerApprovalStatus outcome, Decider decider, String note, Instant now) {
    requireOpen(now);
    if (decider == null) {
      throw new IllegalArgumentException("Who decided is required");
    }
    this.status = outcome;
    this.decisionChannel = decider.channel();
    this.decidedAt = now;
    this.decidedByName = clip(trimmed(decider.name()), 200);
    this.decidedByEmail = clip(trimmed(decider.email()), 255);
    this.decidedByUserId = decider.userId();
    this.ipAddress = clip(trimmed(decider.ipAddress()), 64);
    this.userAgent = clip(trimmed(decider.userAgent()), 500);
    this.customerNote = note;
    this.closedAt = now;
    clearCode();
  }

  private void clearCode() {
    this.codeHash = null;
    this.codeExpiresAt = null;
    this.codeAttempts = 0;
    this.sessionHash = null;
    this.sessionExpiresAt = null;
  }

  private void requireStatus(CustomerApprovalStatus expected) {
    if (status != expected) {
      throw OrderDomainException.stage(
          "APPROVAL_CLOSED", "This approval request is " + status + ", not " + expected);
    }
  }

  private static OrderDomainException proposalExpired() {
    return OrderDomainException.stage(
        "PROPOSAL_EXPIRED",
        "Planning's proposal is no longer valid; planning has to confirm the date again");
  }

  private static String requireEmail(String email) {
    String value = trimmed(email);
    if (value == null || value.length() > 255 || !value.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
      throw OrderDomainException.stage(
          "CONTACT_EMAIL_REQUIRED", "The order's contact needs a valid e-mail address");
    }
    return value;
  }

  private static String trimmed(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static String clip(String value, int max) {
    return value == null || value.length() <= max ? value : value.substring(0, max);
  }

  @Override
  protected String getModuleCode() {
    return "SCA";
  }
}
