package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.common.infrastructure.persistence.SystemTransactionExecutor;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.platform.user.domain.SystemUser;
import com.fabricmanagement.sales.common.exception.OrderDomainException;
import com.fabricmanagement.sales.salesorder.domain.CustomerApproval;
import com.fabricmanagement.sales.salesorder.domain.CustomerApprovalChannel;
import com.fabricmanagement.sales.salesorder.domain.CustomerApprovalStatus;
import com.fabricmanagement.sales.salesorder.domain.OrderVersion;
import com.fabricmanagement.sales.salesorder.domain.OrderVersionContent;
import com.fabricmanagement.sales.salesorder.dto.PublicOrderApprovalDtos;
import com.fabricmanagement.sales.salesorder.infra.repository.CustomerApprovalRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderVersionRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The customer's page behind the e-mailed link. The link alone shows only who asks and for which
 * order; the content opens after a one-time code sent to the address the link went to, and a
 * decision needs the session that code started. The link's tenant is found from the link's hash
 * before any transaction is opened, so row-level security holds for everything after it.
 */
@Service
@RequiredArgsConstructor
public class PublicOrderApprovalService {

  private static final SecureRandom RANDOM = new SecureRandom();
  private static final Pattern TOKEN = Pattern.compile("^[0-9a-f]{64}$");

  private final CustomerApprovalRepository approvals;
  private final OrderVersionRepository versions;
  private final SystemTransactionExecutor system;
  private final TransactionTemplate transactions;
  private final CustomerApprovalDecisionService decisions;
  private final CustomerApprovalMailer mailer;
  private final Clock clock;
  private final ApproverAuthorities approvers;

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public PublicOrderApprovalDtos.LinkView view(String token) {
    String hash = hashOf(token);
    return inTenant(
        hash, () -> transactions.execute(status -> linkView(approvalOf(hash), clock.instant())));
  }

  /** Sends a one-time code to the address the link went to. */
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public PublicOrderApprovalDtos.LinkView sendCode(String token) {
    String hash = hashOf(token);
    return inTenant(
        hash,
        () ->
            transactions.execute(
                status -> {
                  Instant now = clock.instant();
                  CustomerApproval approval = approvalOf(hash);
                  // No code once the representative's authority is no longer in force; the
                  // authority is held while the code goes out, like every other send.
                  approvers.holdValid(approval, now);
                  String code = String.format("%06d", RANDOM.nextInt(1_000_000));
                  approval.codeIssued(codeHash(approval, code), now);
                  approvals.save(approval);
                  OrderVersionContent content = versionOf(approval).getContent();
                  mailer.sendCode(
                      TenantContext.requireTenantId(),
                      approval.getRecipientEmail(),
                      content.orderNumber(),
                      content.sellerName(),
                      code);
                  return linkView(approval, now);
                }));
  }

  /**
   * Checks the code. A right code opens the version and starts a session for the decision; a wrong
   * one is counted and refused.
   */
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public PublicOrderApprovalDtos.Verified verify(String token, String code) {
    String hash = hashOf(token);
    Checked checked =
        inTenant(
            hash,
            () ->
                transactions.execute(
                    status -> {
                      Instant now = clock.instant();
                      CustomerApproval approval = approvalOf(hash);
                      // A code received earlier — even one still queued when the authority
                      // ended — opens nothing. Checked under the authority's lock: a right code
                      // answers with the content, so it never races an ending.
                      approvers.holdValid(approval, now);
                      String session = CustomerApprovalService.randomHex();
                      boolean right =
                          approval.verifyCode(
                              codeHash(approval, code),
                              OrderVersionSnapshotter.sha256(session),
                              now);
                      approvals.save(approval);
                      return right
                          ? new Checked(
                              new PublicOrderApprovalDtos.Verified(
                                  session,
                                  approval.getSessionExpiresAt(),
                                  versionView(approval, now)),
                              0)
                          : new Checked(null, approval.codeAttemptsLeft());
                    }));
    if (checked.verified() == null) {
      // The wrong attempt is counted (committed above) before the refusal.
      throw OrderDomainException.rule(
          "CODE_INVALID", "The code is not right; " + checked.attemptsLeft() + " attempts left");
    }
    return checked.verified();
  }

  /** The version again, for a page reloaded within the verified session. */
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public PublicOrderApprovalDtos.VersionView version(String token, String session) {
    String hash = hashOf(token);
    return inTenant(
        hash,
        () ->
            transactions.execute(
                status -> {
                  Instant now = clock.instant();
                  CustomerApproval approval = approvalOf(hash);
                  if (!approval.hasSession(OrderVersionSnapshotter.sha256(session), now)) {
                    throw sessionRequired();
                  }
                  // The content is shown only under the authority's lock, never while it ends.
                  approvers.holdValid(approval, now);
                  return versionView(approval, now);
                }));
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public PublicOrderApprovalDtos.LinkView approve(
      String token, String session, String ipAddress, String userAgent) {
    String hash = hashOf(token);
    return inTenant(
        hash,
        () -> {
          CustomerApproval.Decider decider = deciderOf(hash, ipAddress, userAgent);
          decisions.approve(idOf(hash), decider, sessionAuthority(session));
          return transactions.execute(status -> linkView(approvalOf(hash), clock.instant()));
        });
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public PublicOrderApprovalDtos.LinkView requestChanges(
      String token, String session, String note, String ipAddress, String userAgent) {
    String hash = hashOf(token);
    return inTenant(
        hash,
        () -> {
          CustomerApproval.Decider decider = deciderOf(hash, ipAddress, userAgent);
          decisions.requestChanges(idOf(hash), decider, note, sessionAuthority(session));
          return transactions.execute(status -> linkView(approvalOf(hash), clock.instant()));
        });
  }

  // ── Internals ──────────────────────────────────────────────────────────

  /** Resolves the link's tenant past row-level security, then runs {@code work} as that tenant. */
  private <T> T inTenant(String hash, Supplier<T> work) {
    UUID tenantId =
        system
            .executeQuery(
                "SELECT tenant_id FROM sales_ord.customer_approval WHERE token_hash = ?",
                (rs, row) -> UUID.fromString(rs.getString("tenant_id")),
                hash)
            .stream()
            .findFirst()
            .orElseThrow(PublicOrderApprovalService::linkNotFound);
    return TenantContext.executeInTenantContext(
        tenantId,
        () -> {
          TenantContext.setCurrentUserId(SystemUser.ID);
          return work.get();
        });
  }

  private CustomerApproval approvalOf(String hash) {
    return approvals
        .findByTenantIdAndTokenHash(TenantContext.requireTenantId(), hash)
        .orElseThrow(PublicOrderApprovalService::linkNotFound);
  }

  private UUID idOf(String hash) {
    return transactions.execute(status -> approvalOf(hash).getId());
  }

  /** The representative is the person the link and the code went to. */
  private CustomerApproval.Decider deciderOf(String hash, String ipAddress, String userAgent) {
    return transactions.execute(
        status -> {
          CustomerApproval approval = approvalOf(hash);
          return new CustomerApproval.Decider(
              CustomerApprovalChannel.EMAIL_LINK,
              approval.getRecipientName(),
              approval.getRecipientEmail(),
              null,
              ipAddress,
              userAgent);
        });
  }

  private static CustomerApprovalDecisionService.Authority sessionAuthority(String session) {
    String presented = session == null ? null : OrderVersionSnapshotter.sha256(session);
    return (approval, now) -> {
      if (!approval.hasSession(presented, now)) {
        throw sessionRequired();
      }
    };
  }

  private OrderVersion versionOf(CustomerApproval approval) {
    return versions
        .findByTenantIdAndId(TenantContext.requireTenantId(), approval.getOrderVersionId())
        .orElseThrow(PublicOrderApprovalService::linkNotFound);
  }

  private PublicOrderApprovalDtos.VersionView versionView(CustomerApproval approval, Instant now) {
    OrderVersion version = versionOf(approval);
    return new PublicOrderApprovalDtos.VersionView(
        version.getVersionNo(),
        version.getContent(),
        linkView(approval, version, now, approvers.isValid(approval, now)));
  }

  private PublicOrderApprovalDtos.LinkView linkView(CustomerApproval approval, Instant now) {
    return linkView(approval, versionOf(approval), now, approvers.isValid(approval, now));
  }

  /**
   * An open link whose representative's authority has ended is shown closed: it can no longer be
   * used. A decision already taken is shown as taken.
   */
  private static PublicOrderApprovalDtos.LinkView linkView(
      CustomerApproval approval, OrderVersion version, Instant now, boolean authorityValid) {
    OrderVersionContent content = version.getContent();
    PublicOrderApprovalDtos.LinkState state = stateOf(approval, now);
    boolean usable = approval.isOpenAt(now) && authorityValid;
    if (state == PublicOrderApprovalDtos.LinkState.OPEN && !authorityValid) {
      state = PublicOrderApprovalDtos.LinkState.CLOSED;
    }
    return new PublicOrderApprovalDtos.LinkView(
        state,
        content.sellerName(),
        content.orderNumber(),
        content.customerName(),
        mask(approval.getRecipientEmail()),
        approval.getLinkExpiresAt(),
        approval.getCodeSentAt(),
        usable ? approval.nextCodeAllowedAt() : null,
        approval.getDecidedAt());
  }

  static PublicOrderApprovalDtos.LinkState stateOf(CustomerApproval approval, Instant now) {
    CustomerApprovalStatus status = approval.getStatus();
    return switch (status) {
      case SENT ->
          approval.isOpenAt(now)
              ? PublicOrderApprovalDtos.LinkState.OPEN
              : PublicOrderApprovalDtos.LinkState.EXPIRED;
      case APPROVED -> PublicOrderApprovalDtos.LinkState.APPROVED;
      case APPROVED_NOT_FULFILLABLE -> PublicOrderApprovalDtos.LinkState.APPROVED_NOT_FULFILLABLE;
      case CHANGES_REQUESTED -> PublicOrderApprovalDtos.LinkState.CHANGES_REQUESTED;
      default -> PublicOrderApprovalDtos.LinkState.CLOSED;
    };
  }

  /** "j***@example.com": enough to recognise the address, not to learn it. */
  static String mask(String email) {
    int at = email == null ? -1 : email.indexOf('@');
    if (at < 1) {
      return "***";
    }
    return email.charAt(0) + "***" + email.substring(at);
  }

  /** The code's hash is bound to its request, so the same digits elsewhere do not match. */
  private static String codeHash(CustomerApproval approval, String code) {
    return OrderVersionSnapshotter.sha256(approval.getId() + ":" + code);
  }

  private static String hashOf(String token) {
    if (token == null || !TOKEN.matcher(token).matches()) {
      throw linkNotFound();
    }
    return OrderVersionSnapshotter.sha256(token);
  }

  private static NotFoundException linkNotFound() {
    return new NotFoundException("This approval link is not valid");
  }

  private static OrderDomainException sessionRequired() {
    return OrderDomainException.stage(
        "SESSION_REQUIRED", "Enter the code we e-mailed you before deciding");
  }

  private record Checked(PublicOrderApprovalDtos.Verified verified, int attemptsLeft) {}
}
