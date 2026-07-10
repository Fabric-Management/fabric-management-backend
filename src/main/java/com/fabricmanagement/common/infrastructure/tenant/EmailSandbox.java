package com.fabricmanagement.common.infrastructure.tenant;

/**
 * A tenant's email-sandbox state, as seen by anything about to send mail on its behalf.
 *
 * @param enabled whether this tenant's mail must never reach the address it is addressed to
 * @param redirectTo where it goes instead; null when {@code enabled} and the tenant has no
 *     registration address, which means the mail must be dropped rather than sent
 */
public record EmailSandbox(boolean enabled, String redirectTo) {

  private static final EmailSandbox OFF = new EmailSandbox(false, null);

  /** The tenant may email whoever it likes. */
  public static EmailSandbox off() {
    return OFF;
  }

  /** The tenant's mail goes to {@code redirectTo} instead of its intended recipient. */
  public static EmailSandbox redirectingTo(String redirectTo) {
    return new EmailSandbox(true, redirectTo);
  }

  /**
   * Sandboxed, but with nowhere to redirect to. Callers must drop the email. Never fall through to
   * the intended recipient — sending is the failure we are preventing; not sending is a missing
   * demo email.
   */
  public static EmailSandbox withoutRecipient() {
    return new EmailSandbox(true, null);
  }

  public boolean mustDrop() {
    return enabled && redirectTo == null;
  }
}
