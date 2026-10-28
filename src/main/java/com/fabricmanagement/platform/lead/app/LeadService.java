package com.fabricmanagement.platform.lead.app;

import com.fabricmanagement.platform.auth.domain.port.SignupEvidencePort;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.lead.domain.CompanyEvidence;
import com.fabricmanagement.platform.lead.domain.Lead;
import com.fabricmanagement.platform.lead.domain.LeadProfileSnapshot;
import com.fabricmanagement.platform.lead.infra.repository.LeadProfileSnapshotRepository;
import com.fabricmanagement.platform.lead.infra.repository.LeadRepository;
import com.fabricmanagement.platform.user.domain.OnboardingStepId;
import com.fabricmanagement.platform.user.domain.port.OnboardingEvidencePort;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Credential-free retention participates in the caller's transaction. No event/email lookup path.
 */
@Service
@RequiredArgsConstructor
public class LeadService implements SignupEvidencePort, OnboardingEvidencePort {
  private final LeadRepository leads;
  private final LeadProfileSnapshotRepository snapshots;

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void capture(SignupCompany signup) {
    var existing = leads.findBySignupId(signup.signupId());
    if (existing.isPresent()) {
      Lead lead = existing.get();
      if (!Objects.equals(lead.getSignupTenantId(), signup.tenantId())
          || !Objects.equals(lead.getSignupUserId(), signup.ownerUserId())
          || !Objects.equals(lead.getSignupIntent(), signup.intent())
          || !Objects.equals(micros(lead.getTermsAcceptedAt()), micros(signup.termsAcceptedAt()))
          || !Objects.equals(lead.getSignupSnapshot(), signup.facts())) {
        throw new PlatformDomainException(
            "Signup correlation is already used", "ONBOARDING_SIGNUP_CONFLICT", 409);
      }
      return;
    }
    var facts = signup.facts();
    var org = facts.organisation();
    var person = facts.registrant();
    Lead lead =
        Lead.create(
            org.organizationName(),
            org.taxId(),
            org.organizationType(),
            person.firstName(),
            person.lastName(),
            person.workEmail(),
            facts.selectedOS(),
            signup.intent(),
            signup.tenantId());
    lead.linkSignup(
        signup.signupId(),
        signup.tenantId(),
        signup.ownerUserId(),
        facts,
        signup.termsAcceptedAt());
    lead = leads.saveAndFlush(lead);
    snapshots.save(
        LeadProfileSnapshot.capture(
            lead.getId(), 0, "SIGNUP", null, null, signup.ownerUserId(), facts, null));
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public CompanyEvidence current(UUID tenantId, UUID signupId) {
    return require(tenantId, signupId).getAcceptedSnapshot();
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void accept(
      UUID tenantId,
      UUID signupId,
      UUID actor,
      UUID operationId,
      OnboardingStepId step,
      CompanyEvidence evidence,
      Instant completedAt) {
    append(
        tenantId,
        signupId,
        actor,
        operationId,
        step,
        evidence,
        completedAt,
        completedAt == null ? "STEP_ACCEPTED" : "FINAL_COMPLETION");
  }

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void correctLegacy(
      UUID tenantId,
      UUID signupId,
      UUID actor,
      UUID operationId,
      CompanyEvidence evidence,
      Instant firstCompletionAt) {
    append(
        tenantId,
        signupId,
        actor,
        operationId,
        OnboardingStepId.REVIEW,
        evidence,
        firstCompletionAt,
        "LEGACY_PROFILE_CORRECTION");
  }

  private void append(
      UUID tenantId,
      UUID signupId,
      UUID actor,
      UUID operationId,
      OnboardingStepId step,
      CompanyEvidence evidence,
      Instant completedAt,
      String source) {
    Lead lead =
        leads
            .findLockedBySignupTenantIdAndSignupId(tenantId, signupId)
            .orElseThrow(
                () ->
                    new PlatformDomainException(
                        "Signup evidence linkage is unresolved",
                        "ONBOARDING_SIGNUP_LINK_UNRESOLVED",
                        409));
    long revision = lead.accept(evidence);
    snapshots.save(
        LeadProfileSnapshot.capture(
            lead.getId(),
            revision,
            source,
            step.name(),
            operationId,
            actor,
            evidence,
            completedAt));
  }

  private Instant micros(Instant timestamp) {
    return timestamp == null ? null : timestamp.truncatedTo(java.time.temporal.ChronoUnit.MICROS);
  }

  private Lead require(UUID tenantId, UUID signupId) {
    return leads
        .findBySignupTenantIdAndSignupId(tenantId, signupId)
        .orElseThrow(
            () ->
                new PlatformDomainException(
                    "Signup evidence linkage is unresolved",
                    "ONBOARDING_SIGNUP_LINK_UNRESOLVED",
                    409));
  }
}
