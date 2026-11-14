package com.fabricmanagement.platform.lead.app;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fabricmanagement.platform.auth.domain.port.SignupEvidencePort.SignupCompany;
import com.fabricmanagement.platform.auth.domain.port.SignupEvidencePort.UnverifiedSignup;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.lead.domain.*;
import com.fabricmanagement.platform.lead.infra.repository.*;
import com.fabricmanagement.platform.organization.domain.OrganizationType;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class LeadServiceTest {
  private final LeadRepository leads = mock(LeadRepository.class);
  private final LeadProfileSnapshotRepository snapshots = mock(LeadProfileSnapshotRepository.class);
  private final LeadService service = new LeadService(leads, snapshots);

  @Test
  void capturesImmutableSignupAndInitialHistoryWithoutCredentials() {
    var signup = signup();
    UUID id = UUID.randomUUID();
    when(leads.saveAndFlush(any()))
        .thenAnswer(
            call -> {
              Lead lead = call.getArgument(0);
              ReflectionTestUtils.setField(lead, "id", id);
              return lead;
            });
    service.capture(signup);
    var capture = ArgumentCaptor.forClass(Lead.class);
    verify(leads).saveAndFlush(capture.capture());
    Lead lead = capture.getValue();
    assertThat(lead.getSignupId()).isEqualTo(signup.signupId());
    assertThat(lead.getSignupTenantId()).isEqualTo(signup.tenantId());
    assertThat(lead.getSignupUserId()).isEqualTo(signup.ownerUserId());
    assertThat(lead.getSignupSnapshot()).isEqualTo(signup.facts());
    assertThat(lead.getAcceptedSnapshot()).isEqualTo(signup.facts());
    assertThat(lead.getTermsAcceptedAt()).isEqualTo(signup.termsAcceptedAt());
    assertThat(lead.getCompanyName()).isEqualTo("Hawthorn Weaving Ltd");
    assertThat(lead.getFirstName()).isEqualTo("Eleanor");
    var history = ArgumentCaptor.forClass(LeadProfileSnapshot.class);
    verify(snapshots).save(history.capture());
    assertThat(history.getValue().getLeadId()).isEqualTo(id);
    assertThat(history.getValue().getSource()).isEqualTo("SIGNUP");
    assertThat(history.getValue().getRevision()).isZero();
  }

  @Test
  void identicalSignupCorrelationIsIdempotentButChangedCompanyConflicts() {
    var signup = signup();
    var org = signup.facts().organisation();
    Lead existing =
        Lead.create(
            org.organizationName(),
            org.taxId(),
            org.organizationType(),
            "Eleanor",
            "Hughes",
            "eleanor.hughes@example.com",
            List.of("FabricOS"),
            "PLAYGROUND",
            signup.tenantId());
    existing.linkSignup(
        signup.signupId(),
        signup.tenantId(),
        signup.ownerUserId(),
        signup.facts(),
        signup.termsAcceptedAt());
    when(leads.findBySignupId(signup.signupId())).thenReturn(Optional.of(existing));
    service.capture(signup);
    verify(leads, never()).saveAndFlush(any());
    verifyNoInteractions(snapshots);
    var changed =
        new SignupCompany(
            signup.signupId(),
            UUID.randomUUID(),
            signup.ownerUserId(),
            "TRIAL",
            signup.facts(),
            signup.termsAcceptedAt());
    assertThatThrownBy(() -> service.capture(changed))
        .isInstanceOfSatisfying(
            PlatformDomainException.class,
            error -> assertThat(error.getErrorCode()).isEqualTo("ONBOARDING_SIGNUP_CONFLICT"));
  }

  @Test
  void sameEmailDoesNotResolveAnotherCompaniesEvidence() {
    var signup = signup();
    assertThatThrownBy(() -> service.current(UUID.randomUUID(), signup.signupId()))
        .isInstanceOfSatisfying(
            PlatformDomainException.class,
            error ->
                assertThat(error.getErrorCode()).isEqualTo("ONBOARDING_SIGNUP_LINK_UNRESOLVED"));
    verify(leads).findBySignupTenantIdAndSignupId(any(), any());
    verify(leads, never()).findByWorkEmail(any());
  }

  @Test
  void aSignupFormWhoseLinkIsNotUsedYetBecomesAnUnverifiedLead() {
    UUID signupId = UUID.randomUUID();
    when(leads.findBySignupId(signupId)).thenReturn(Optional.empty());

    service.recordUnverified(unverified(signupId, "Hawthorn Weaving Ltd"));

    var capture = ArgumentCaptor.forClass(Lead.class);
    verify(leads).save(capture.capture());
    Lead lead = capture.getValue();
    assertThat(lead.getSignupId()).isEqualTo(signupId);
    assertThat(lead.isVerified()).isFalse();
    assertThat(lead.getSignupTenantId()).isNull();
    assertThat(lead.getSignupSnapshot()).isNull();
    assertThat(lead.getWorkEmail()).isEqualTo("owner@example.com");
    assertThat(lead.getCompanyName()).isEqualTo("Hawthorn Weaving Ltd");
    verifyNoInteractions(snapshots);
  }

  @Test
  void theFormSentAgainRefreshesTheSameUnverifiedLead() {
    UUID signupId = UUID.randomUUID();
    Lead existing = unverifiedLead(signupId, "First Name Ltd");
    when(leads.findBySignupId(signupId)).thenReturn(Optional.of(existing));

    service.recordUnverified(unverified(signupId, "Second Name Ltd"));

    assertThat(existing.getCompanyName()).isEqualTo("Second Name Ltd");
    verify(leads, never()).save(any());
  }

  @Test
  void linkingTheSignupToItsTenantVerifiesTheLead() {
    UUID signupId = UUID.randomUUID();
    Lead lead = unverifiedLead(signupId, "Hawthorn Weaving Ltd");
    var completed = signup();
    UUID tenantId = completed.tenantId();

    lead.linkSignup(signupId, tenantId, completed.ownerUserId(), completed.facts(), Instant.now());

    assertThat(lead.isVerified()).isTrue();
    assertThat(lead.getSignupTenantId()).isEqualTo(tenantId);
    assertThat(lead.getSignupSnapshot()).isEqualTo(completed.facts());
  }

  @Test
  void aLeadThatIsAlreadyVerifiedIsNotRewrittenByTheForm() {
    UUID signupId = UUID.randomUUID();
    Lead lead = unverifiedLead(signupId, "Hawthorn Weaving Ltd");
    var completed = signup();
    lead.linkSignup(
        signupId, completed.tenantId(), completed.ownerUserId(), completed.facts(), Instant.now());
    when(leads.findBySignupId(signupId)).thenReturn(Optional.of(lead));

    assertThatThrownBy(() -> service.recordUnverified(unverified(signupId, "Changed Ltd")))
        .isInstanceOf(PlatformDomainException.class);
    assertThat(lead.getCompanyName()).isEqualTo("Hawthorn Weaving Ltd");
  }

  @Test
  void completingTheSignupLinksAndVerifiesTheLeadRecordedAtFormTime() {
    var signup = signup();
    Lead lead = unverifiedLead(signup.signupId(), "Hawthorn Weaving Ltd");
    UUID leadId = UUID.randomUUID();
    ReflectionTestUtils.setField(lead, "id", leadId);
    when(leads.findBySignupId(signup.signupId())).thenReturn(Optional.of(lead));
    when(leads.saveAndFlush(lead)).thenReturn(lead);

    service.capture(signup);

    assertThat(lead.isVerified()).isTrue();
    assertThat(lead.getSignupTenantId()).isEqualTo(signup.tenantId());
    assertThat(lead.getSignupUserId()).isEqualTo(signup.ownerUserId());
    assertThat(lead.getSignupSnapshot()).isEqualTo(signup.facts());
    assertThat(lead.getTermsAcceptedAt()).isEqualTo(signup.termsAcceptedAt());
    assertThat(lead.getTrialTenantId()).isEqualTo(signup.tenantId());
    var history = ArgumentCaptor.forClass(LeadProfileSnapshot.class);
    verify(snapshots).save(history.capture());
    assertThat(history.getValue().getLeadId()).isEqualTo(leadId);
    assertThat(history.getValue().getSource()).isEqualTo("SIGNUP");
    assertThat(history.getValue().getRevision()).isZero();
    verify(leads, never()).save(any());
  }

  private UnverifiedSignup unverified(UUID signupId, String company) {
    return new UnverifiedSignup(
        signupId,
        company,
        "GB123456789",
        OrganizationType.WEAVER,
        "Eleanor",
        "Hughes",
        "owner@example.com",
        List.of("FabricOS"),
        "PLAYGROUND");
  }

  private Lead unverifiedLead(UUID signupId, String company) {
    return Lead.createUnverified(
        signupId,
        company,
        "GB123456789",
        OrganizationType.WEAVER,
        "Eleanor",
        "Hughes",
        "owner@example.com",
        List.of("FabricOS"),
        "PLAYGROUND");
  }

  private SignupCompany signup() {
    var facts =
        new CompanyEvidence(
            new CompanyEvidence.Organisation(
                "Hawthorn Weaving Ltd",
                "GB123456789",
                OrganizationType.WEAVER,
                null,
                null,
                null,
                null,
                null),
            null,
            new CompanyEvidence.Registrant("Eleanor", "Hughes", "eleanor.hughes@example.com", null),
            null,
            List.of("FabricOS"),
            Set.of("organizationName", "taxId", "organizationType", "workEmail", "selectedOS"));
    return new SignupCompany(
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        "PLAYGROUND",
        facts,
        Instant.now());
  }
}
