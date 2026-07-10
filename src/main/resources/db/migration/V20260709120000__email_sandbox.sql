-- EMAIL-SANDBOX-1: every email leaving a sandboxed tenant is redirected to the address
-- that tenant registered with. See docs/platform/tickets/EMAIL-SANDBOX-1-playground-email-redirection.md
--
-- An explicit column rather than a reuse of demo_mode: demo_mode means "this tenant holds demo
-- data", and TRIAL-SIGNUP will make that true for paying trial customers, whose invitation emails
-- must reach the people they invite.

ALTER TABLE common_tenant.common_tenant
    ADD COLUMN IF NOT EXISTS email_sandboxed BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN common_tenant.common_tenant.email_sandboxed IS
    'When true, every outbound email for this tenant is redirected to billing_email. '
    'Set for playground tenants so a visitor cannot address mail to a third party.';

-- Every tenant that is a playground today, by either of the two signals that exist.
UPDATE common_tenant.common_tenant
SET email_sandboxed = TRUE
WHERE (type = 'PLAYGROUND' OR demo_mode = TRUE)
  AND email_sandboxed = FALSE;

-- Keep the address the email was headed for. A redirected email that hides its intended
-- recipient is a debugging trap, and a demo that lies about what the product would have done.
ALTER TABLE common_communication.communication_email_outbox
    ADD COLUMN IF NOT EXISTS original_recipient VARCHAR(255);

COMMENT ON COLUMN common_communication.communication_email_outbox.original_recipient IS
    'The recipient this email was addressed to before sandbox redirection. NULL when not redirected.';
