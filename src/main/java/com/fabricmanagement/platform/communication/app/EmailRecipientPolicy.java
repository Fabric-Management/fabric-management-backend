package com.fabricmanagement.platform.communication.app;

import com.fabricmanagement.common.infrastructure.tenant.EmailSandbox;
import com.fabricmanagement.common.infrastructure.tenant.TenantAccessPort;
import com.fabricmanagement.common.util.PiiMaskingUtil;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Decides where an email may actually be sent.
 *
 * <p>In a sandboxed tenant — the playground — every message is redirected to the address the
 * prospect registered with. They see the quote approval, the invitation, the notification digest; a
 * stranger never does. Without this, a visitor can address mail to anyone and it leaves our domain
 * signed with our SPF and DKIM.
 *
 * <p><b>Call this while tenant context still exists</b>, i.e. when the email is created, not when
 * it is sent. The outbox worker (`@Scheduled`) has no ambient tenant, and rebuilding one from the
 * stored row would mean trusting a row that was written before this decision was made. Async
 * senders are safe — {@code AsyncConfig} installs a {@code ContextPropagatingTaskDecorator} — but
 * relying on that at send time buys nothing and breaks the moment someone adds an executor.
 *
 * @see docs/platform/tickets/EMAIL-SANDBOX-1-playground-email-redirection.md
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EmailRecipientPolicy {

  private final TenantAccessPort tenantAccessPort;

  /**
   * Where an email may go, and under what subject.
   *
   * @param dropped when true the email must not be sent at all; the other fields are meaningless
   * @param recipient the address to send to
   * @param intendedRecipient the address the caller asked for; null unless redirected
   * @param subject the subject to send under, annotated when redirected
   */
  public record Resolution(
      boolean dropped, String recipient, String intendedRecipient, String subject) {

    public boolean redirected() {
      return intendedRecipient != null;
    }
  }

  /**
   * Resolve for a caller that already knows its tenant.
   *
   * <p>The tenant id is required because a missing tenant context must not read as "not sandboxed".
   */
  public Resolution resolveFor(UUID tenantId, String intendedRecipient, String subject) {
    if (tenantId == null) {
      throw new IllegalStateException(
          "Tenant id must be known before deciding where an email may be sent");
    }

    EmailSandbox sandbox = tenantAccessPort.emailSandbox(tenantId);

    if (!sandbox.enabled()) {
      return new Resolution(false, intendedRecipient, null, subject);
    }

    if (sandbox.mustDrop()) {
      log.warn(
          "🛡️ [SANDBOX] Dropping email for tenant {}: sandboxed with no redirect address."
              + " Intended recipient={}, subject={}",
          tenantId,
          PiiMaskingUtil.maskEmail(intendedRecipient),
          subject);
      return new Resolution(true, null, intendedRecipient, subject);
    }

    log.info(
        "🛡️ [SANDBOX] Redirecting email for tenant {}: {} → {}",
        tenantId,
        PiiMaskingUtil.maskEmail(intendedRecipient),
        PiiMaskingUtil.maskEmail(sandbox.redirectTo()));

    return new Resolution(
        false, sandbox.redirectTo(), intendedRecipient, annotate(subject, intendedRecipient));
  }

  /**
   * Resolve for a channel that carries no subject — verification codes.
   *
   * <p>A sandboxed tenant may not send an SMS at all: there is no phone number to redirect to, and
   * an unsolicited text is exactly the abuse this policy exists to stop. Callers must check {@link
   * Resolution#dropped()} rather than assuming a recipient comes back.
   */
  public Resolution resolveWithoutSubject(
      UUID tenantId, String intendedRecipient, boolean isEmail) {
    Resolution resolution = resolveFor(tenantId, intendedRecipient, "");
    if (!resolution.redirected() || resolution.dropped()) {
      return resolution;
    }
    if (!isEmail) {
      log.warn(
          "🛡️ [SANDBOX] Dropping SMS for a sandboxed tenant; there is nowhere safe to send it.");
      return new Resolution(true, null, intendedRecipient, "");
    }
    return new Resolution(false, resolution.recipient(), intendedRecipient, "");
  }

  private String annotate(String subject, String intendedRecipient) {
    // The prospect should see that the product *would* have emailed their customer.
    return "[Playground → %s] %s".formatted(intendedRecipient, subject == null ? "" : subject);
  }
}
