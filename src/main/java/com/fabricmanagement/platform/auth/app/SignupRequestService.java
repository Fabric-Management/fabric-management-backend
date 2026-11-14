package com.fabricmanagement.platform.auth.app;

import com.fabricmanagement.common.infrastructure.config.FrontendUrlProvider;
import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.LocalizationContext;
import com.fabricmanagement.common.infrastructure.web.exception.TaxIdAlreadyExistsException;
import com.fabricmanagement.platform.auth.domain.PendingSignup;
import com.fabricmanagement.platform.auth.domain.PendingSignupRequest;
import com.fabricmanagement.platform.auth.domain.event.ExistingAccountSignupAttemptedEvent;
import com.fabricmanagement.platform.auth.domain.event.SignupVerificationRequestedEvent;
import com.fabricmanagement.platform.auth.domain.port.SignupEvidencePort;
import com.fabricmanagement.platform.auth.domain.port.SignupEvidencePort.UnverifiedSignup;
import com.fabricmanagement.platform.auth.dto.SelfSignupRequest;
import com.fabricmanagement.platform.auth.infra.repository.PendingSignupRepository;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.organization.api.facade.OrganizationFacade;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Takes a public self-service signup form (SIGNUP-DEFER-1 / TD-18).
 *
 * <p>A person who has not proved the e-mail address gets no tenant. The form writes one small
 * {@link PendingSignup} row, an unverified sales lead, and mails a setup link; the company, its
 * users and demo data are built only when that link is used.
 *
 * <p>The caller always gets the same answer ("check your e-mail"), whatever the state of the
 * address, so the form never reveals who is a customer. The only refusals are about the form
 * itself: terms not accepted, or a company tax number that is already taken.
 *
 * <h2>What happens behind the answer</h2>
 *
 * <ul>
 *   <li>The address already has a login: nothing is stored; a "sign in and add the company" mail is
 *       sent.
 *   <li>The address has an open pending signup: it takes the latest form, a new link is issued and
 *       the old link stops working (D1). Bounded by {@link #MAX_LINKS_PER_PENDING_SIGNUP}.
 *   <li>Otherwise: a new pending signup and an unverified lead are written and the link is sent.
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SignupRequestService {

  /** How long a setup link works (D4). */
  static final Duration LINK_LIFETIME = Duration.ofHours(24);

  /**
   * Links mailed for one pending signup. Together with the per-address request limit on the
   * endpoint this stops the form being used to mail the same person again and again.
   */
  static final int MAX_LINKS_PER_PENDING_SIGNUP = 5;

  private static final String TAX_ID_TAKEN = "Organization with this tax ID already exists";

  private final PendingSignupRepository pendingSignups;
  private final OrganizationFacade organizationFacade;
  private final IdentityProvisioningService identityProvisioningService;
  private final SignupEvidencePort evidence;
  private final DomainEventPublisher eventPublisher;
  private final FrontendUrlProvider frontendUrlProvider;
  private final ObjectMapper objectMapper;

  @Transactional
  public void request(SelfSignupRequest form) {
    if (!Boolean.TRUE.equals(form.getAcceptedTerms())) {
      throw new PlatformDomainException(
          "Terms and conditions must be accepted", "AUTH_TERMS_NOT_ACCEPTED", 400);
    }

    String email = form.getEmail().strip().toLowerCase(Locale.ROOT);
    String taxId = form.getTaxId().strip();
    String taxKey = taxKey(taxId);
    Instant now = Instant.now();

    // Unique indexes cannot test the clock: an expired row must go before it can block a new one.
    pendingSignups.deleteExpiredHolding(now, email, taxKey);

    // The tax number is judged before the address, so that the answer cannot depend on whether
    // the address has an account (a refusal for a taken number is the same for every address).
    requireTaxIdFree(taxId, taxKey, email);

    if (identityProvisioningService.isEmailRegistered(email)) {
      eventPublisher.publish(
          new ExistingAccountSignupAttemptedEvent(
              TenantContext.SYSTEM_TENANT_ID,
              email,
              form.getFirstName(),
              frontendUrlProvider.buildUrl("/login"),
              LocalizationContext.getLocale()));
      log.info("Signup form for an address that already has an account: no pending signup");
      return;
    }

    PendingSignup open = pendingSignups.findByEmailAndCompletedAtIsNull(email).orElse(null);
    if (open != null) {
      replaceOpen(open, form, taxId, now);
      return;
    }

    UUID id = UUID.randomUUID();
    SignupLinkToken.Issued token = SignupLinkToken.issue();
    int inserted =
        pendingSignups.insertIfAbsent(
            id, email, taxId, toJson(toRequest(form)), token.hash(), now, now.plus(LINK_LIFETIME));
    if (inserted == 0) {
      // A simultaneous signup took this e-mail or tax number between our check and our insert.
      PendingSignup winner = pendingSignups.findByEmailAndCompletedAtIsNull(email).orElse(null);
      if (winner == null) {
        throw new TaxIdAlreadyExistsException(TAX_ID_TAKEN);
      }
      replaceOpen(winner, form, taxId, now);
      return;
    }

    recordLead(id, form, taxId, email);
    publishLink(form, email, token);
    log.info("Pending signup stored: pendingSignupId={}", id);
  }

  private void replaceOpen(PendingSignup open, SelfSignupRequest form, String taxId, Instant now) {
    SignupLinkToken.Issued token = SignupLinkToken.issue();
    int replaced;
    try {
      replaced =
          pendingSignups.replaceIfUnderLimit(
              open.getId(),
              taxId,
              toJson(toRequest(form)),
              token.hash(),
              now,
              now.plus(LINK_LIFETIME),
              now,
              MAX_LINKS_PER_PENDING_SIGNUP);
    } catch (DataIntegrityViolationException taxTakenByAnotherSignup) {
      throw new TaxIdAlreadyExistsException(TAX_ID_TAKEN);
    }
    if (replaced == 0) {
      // Same answer to the caller, but no further mail goes to this address.
      log.warn(
          "Pending signup not replaced (link limit reached): pendingSignupId={}", open.getId());
      return;
    }

    recordLead(open.getId(), form, taxId, open.getEmail());
    publishLink(form, open.getEmail(), token);
    log.info("Pending signup replaced and a new link issued: pendingSignupId={}", open.getId());
  }

  /** A tax number held by an organisation, or by an open signup of another address, is taken. */
  private void requireTaxIdFree(String taxId, String taxKey, String email) {
    if (organizationFacade.existsByTaxId(taxId)
        || pendingSignups.existsOpenForTaxKeyOfOtherEmail(taxKey, email)) {
      throw new TaxIdAlreadyExistsException(TAX_ID_TAKEN);
    }
  }

  private void recordLead(
      UUID pendingSignupId, SelfSignupRequest form, String taxId, String email) {
    evidence.recordUnverified(
        new UnverifiedSignup(
            pendingSignupId,
            form.getOrganizationName(),
            taxId,
            form.getOrganizationType(),
            form.getFirstName(),
            form.getLastName(),
            email,
            form.getSelectedOS(),
            form.getIntent().name()));
  }

  private void publishLink(SelfSignupRequest form, String email, SignupLinkToken.Issued token) {
    eventPublisher.publish(
        new SignupVerificationRequestedEvent(
            TenantContext.SYSTEM_TENANT_ID,
            email,
            form.getFirstName(),
            form.getOrganizationName(),
            frontendUrlProvider.buildUrl("/setup?token=" + token.raw()),
            LocalizationContext.getLocale()));
  }

  private static PendingSignupRequest toRequest(SelfSignupRequest form) {
    return new PendingSignupRequest(
        form.getOrganizationName(),
        form.getOrganizationType(),
        form.getFirstName(),
        form.getLastName(),
        form.getSelectedOS() == null ? null : List.copyOf(form.getSelectedOS()),
        form.getIntent().name());
  }

  /** The tax number as the unique index sees it: trimmed and lower-cased. */
  static String taxKey(String taxId) {
    return taxId.strip().toLowerCase(Locale.ROOT);
  }

  private String toJson(PendingSignupRequest request) {
    try {
      return objectMapper.writeValueAsString(request);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Pending signup request cannot be serialised", e);
    }
  }
}
