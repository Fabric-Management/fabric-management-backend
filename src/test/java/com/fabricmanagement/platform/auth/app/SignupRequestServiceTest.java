package com.fabricmanagement.platform.auth.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.config.FrontendUrlProvider;
import com.fabricmanagement.common.infrastructure.events.DomainEvent;
import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.TaxIdAlreadyExistsException;
import com.fabricmanagement.platform.auth.domain.PendingSignup;
import com.fabricmanagement.platform.auth.domain.PendingSignupRequest;
import com.fabricmanagement.platform.auth.domain.event.ExistingAccountSignupAttemptedEvent;
import com.fabricmanagement.platform.auth.domain.event.SignupVerificationRequestedEvent;
import com.fabricmanagement.platform.auth.domain.port.SignupEvidencePort;
import com.fabricmanagement.platform.auth.domain.port.SignupEvidencePort.UnverifiedSignup;
import com.fabricmanagement.platform.auth.dto.SelfSignupRequest;
import com.fabricmanagement.platform.auth.dto.SignupIntent;
import com.fabricmanagement.platform.auth.infra.repository.PendingSignupRepository;
import com.fabricmanagement.platform.common.exception.PlatformDomainException;
import com.fabricmanagement.platform.organization.api.facade.OrganizationFacade;
import com.fabricmanagement.platform.organization.domain.OrganizationType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * SIGNUP-DEFER-1: what a public signup form does behind its one answer. The database rules (the
 * partial unique indexes, row security, the real mail outbox) are covered by {@code
 * SignupRequestRealRolesIT}.
 */
@DisplayName("SignupRequestService")
class SignupRequestServiceTest {

  private final PendingSignupRepository pendingSignups = mock(PendingSignupRepository.class);
  private final OrganizationFacade organizations = mock(OrganizationFacade.class);
  private final IdentityProvisioningService identities = mock(IdentityProvisioningService.class);
  private final SignupEvidencePort evidence = mock(SignupEvidencePort.class);
  private final DomainEventPublisher events = mock(DomainEventPublisher.class);
  private final FrontendUrlProvider urls = mock(FrontendUrlProvider.class);

  private final SignupRequestService service =
      new SignupRequestService(
          pendingSignups, organizations, identities, evidence, events, urls, new ObjectMapper());

  SignupRequestServiceTest() {
    when(urls.buildUrl(anyString()))
        .thenAnswer(call -> "https://app.test" + call.<String>getArgument(0));
  }

  @Test
  @DisplayName("terms that are not accepted are refused before anything is read or written")
  void termsNotAccepted() {
    SelfSignupRequest form = form("owner@example.com", "TAX-1");
    form.setAcceptedTerms(false);

    assertThatThrownBy(() -> service.request(form))
        .isInstanceOfSatisfying(
            PlatformDomainException.class,
            e -> assertThat(e.getErrorCode()).isEqualTo("AUTH_TERMS_NOT_ACCEPTED"));

    verifyNoInteractions(pendingSignups, identities, evidence, events);
  }

  @Test
  @DisplayName("an address with an account stores nothing and is told to sign in")
  void addressWithAnAccount() {
    when(identities.isEmailRegistered("owner@example.com")).thenReturn(true);

    service.request(form("  Owner@Example.com ", "TAX-1"));

    DomainEvent published = singleEvent();
    assertThat(published).isInstanceOf(ExistingAccountSignupAttemptedEvent.class);
    var event = (ExistingAccountSignupAttemptedEvent) published;
    assertThat(event.getRecipientEmail()).isEqualTo("owner@example.com");
    assertThat(event.getSignInUrl()).isEqualTo("https://app.test/login");
    assertThat(event.getTenantId()).isEqualTo(TenantContext.SYSTEM_TENANT_ID);
    verify(pendingSignups, never()).insertIfAbsent(any(), any(), any(), any(), any(), any(), any());
    verify(pendingSignups, never())
        .replaceIfUnderLimit(any(), any(), any(), any(), any(), any(), any(), anyInt());
    verifyNoInteractions(evidence);
  }

  @Test
  @DisplayName("a taken tax number is refused before the address is looked at")
  void taxNumberIsJudgedBeforeTheAddress() {
    // An account holder must not be told apart from anyone else by the answer: the refusal for a
    // taken tax number comes first, so the account lookup never runs.
    when(organizations.existsByTaxId("TAX-1")).thenReturn(true);

    assertThatThrownBy(() -> service.request(form("owner@example.com", "TAX-1")))
        .isInstanceOf(TaxIdAlreadyExistsException.class);

    verifyNoInteractions(identities, evidence, events);
  }

  @Test
  @DisplayName("a new address stores a pending signup and an unverified lead and mails the link")
  void newAddress() {
    when(pendingSignups.findByEmailAndCompletedAtIsNull("owner@example.com"))
        .thenReturn(Optional.empty());
    when(pendingSignups.insertIfAbsent(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(1);

    Instant before = Instant.now();
    service.request(form("  Owner@Example.com ", "  TAX-1 "));

    // Expired rows holding the address or tax number are cleared first.
    InOrder order = inOrder(pendingSignups);
    order.verify(pendingSignups).deleteExpiredHolding(any(), eq("owner@example.com"), eq("tax-1"));
    var id = ArgumentCaptor.forClass(UUID.class);
    var tax = ArgumentCaptor.forClass(String.class);
    var json = ArgumentCaptor.forClass(String.class);
    var hash = ArgumentCaptor.forClass(String.class);
    var expires = ArgumentCaptor.forClass(Instant.class);
    order
        .verify(pendingSignups)
        .insertIfAbsent(
            id.capture(),
            eq("owner@example.com"),
            tax.capture(),
            json.capture(),
            hash.capture(),
            any(),
            expires.capture());
    assertThat(tax.getValue()).isEqualTo("TAX-1");
    assertThat(json.getValue()).contains("Hawthorn Weaving Ltd").contains("VERTICAL_MILL");
    assertThat(Duration.between(before, expires.getValue()))
        .isBetween(Duration.ofHours(24), Duration.ofHours(24).plusMinutes(1));

    var lead = ArgumentCaptor.forClass(UnverifiedSignup.class);
    verify(evidence).recordUnverified(lead.capture());
    assertThat(lead.getValue().signupId()).isEqualTo(id.getValue());
    assertThat(lead.getValue().workEmail()).isEqualTo("owner@example.com");
    assertThat(lead.getValue().taxId()).isEqualTo("TAX-1");

    var event = (SignupVerificationRequestedEvent) singleEvent();
    assertThat(event.getTenantId()).isEqualTo(TenantContext.SYSTEM_TENANT_ID);
    assertThat(event.getRecipientEmail()).isEqualTo("owner@example.com");
    // Only the hash is stored; the link carries the secret that hashes to it.
    assertThat(hash.getValue()).isNotEqualTo(tokenIn(event.getSetupUrl()));
    assertThat(SignupLinkToken.hash(tokenIn(event.getSetupUrl()))).isEqualTo(hash.getValue());
  }

  @Test
  @DisplayName("the same address again takes the latest form and kills the old link")
  void sameAddressAgainReplaces() {
    PendingSignup open = openSignup("owner@example.com", "TAX-OLD", 1);
    String oldHash = open.getTokenHash();
    when(pendingSignups.findByEmailAndCompletedAtIsNull("owner@example.com"))
        .thenReturn(Optional.of(open));
    when(pendingSignups.replaceIfUnderLimit(
            any(), any(), any(), any(), any(), any(), any(), anyInt()))
        .thenReturn(1);

    service.request(form("owner@example.com", "TAX-NEW"));

    var tax = ArgumentCaptor.forClass(String.class);
    var json = ArgumentCaptor.forClass(String.class);
    var hash = ArgumentCaptor.forClass(String.class);
    verify(pendingSignups)
        .replaceIfUnderLimit(
            eq(open.getId()),
            tax.capture(),
            json.capture(),
            hash.capture(),
            any(),
            any(),
            any(),
            eq(SignupRequestService.MAX_LINKS_PER_PENDING_SIGNUP));
    assertThat(tax.getValue()).isEqualTo("TAX-NEW");
    assertThat(json.getValue()).contains("Hawthorn Weaving Ltd");
    assertThat(hash.getValue()).isNotEqualTo(oldHash);
    verify(pendingSignups, never()).insertIfAbsent(any(), any(), any(), any(), any(), any(), any());

    var lead = ArgumentCaptor.forClass(UnverifiedSignup.class);
    verify(evidence).recordUnverified(lead.capture());
    assertThat(lead.getValue().signupId()).isEqualTo(open.getId());

    var event = (SignupVerificationRequestedEvent) singleEvent();
    assertThat(SignupLinkToken.hash(tokenIn(event.getSetupUrl()))).isEqualTo(hash.getValue());
  }

  @Test
  @DisplayName("at the link limit the answer is the same but no lead is touched and no mail sent")
  void linkLimit() {
    PendingSignup open =
        openSignup("owner@example.com", "TAX-1", SignupRequestService.MAX_LINKS_PER_PENDING_SIGNUP);
    when(pendingSignups.findByEmailAndCompletedAtIsNull("owner@example.com"))
        .thenReturn(Optional.of(open));
    when(pendingSignups.replaceIfUnderLimit(
            any(), any(), any(), any(), any(), any(), any(), anyInt()))
        .thenReturn(0);

    service.request(form("owner@example.com", "TAX-1"));

    verifyNoInteractions(evidence, events);
  }

  @Test
  @DisplayName("a tax number that belongs to an organisation is refused")
  void taxNumberOfAnOrganisation() {
    when(organizations.existsByTaxId("TAX-1")).thenReturn(true);

    assertThatThrownBy(() -> service.request(form("owner@example.com", "TAX-1")))
        .isInstanceOf(TaxIdAlreadyExistsException.class);

    verify(pendingSignups, never()).insertIfAbsent(any(), any(), any(), any(), any(), any(), any());
    verifyNoInteractions(evidence, events);
  }

  @Test
  @DisplayName("a tax number held by an open signup of another address is refused")
  void taxNumberHeldByAnotherAddress() {
    when(pendingSignups.existsOpenForTaxKeyOfOtherEmail("tax-1", "owner@example.com"))
        .thenReturn(true);

    assertThatThrownBy(() -> service.request(form("owner@example.com", "TAX-1")))
        .isInstanceOf(TaxIdAlreadyExistsException.class);

    verifyNoInteractions(evidence, events);
  }

  @Test
  @DisplayName("a replacement may not take a tax number that is held elsewhere")
  void replacementWithATakenTaxNumber() {
    PendingSignup open = openSignup("owner@example.com", "TAX-OLD", 1);
    when(pendingSignups.findByEmailAndCompletedAtIsNull("owner@example.com"))
        .thenReturn(Optional.of(open));
    when(organizations.existsByTaxId("TAX-NEW")).thenReturn(true);

    assertThatThrownBy(() -> service.request(form("owner@example.com", "TAX-NEW")))
        .isInstanceOf(TaxIdAlreadyExistsException.class);

    verify(pendingSignups, never())
        .replaceIfUnderLimit(any(), any(), any(), any(), any(), any(), any(), anyInt());
    verifyNoInteractions(evidence, events);
  }

  @Test
  @DisplayName("losing a race on the same address becomes a replacement of the winner's row")
  void raceOnTheSameAddress() {
    PendingSignup winner = openSignup("owner@example.com", "TAX-OTHER", 1);
    when(pendingSignups.findByEmailAndCompletedAtIsNull("owner@example.com"))
        .thenReturn(Optional.empty(), Optional.of(winner));
    when(pendingSignups.insertIfAbsent(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(0);
    when(pendingSignups.replaceIfUnderLimit(
            any(), any(), any(), any(), any(), any(), any(), anyInt()))
        .thenReturn(1);

    service.request(form("owner@example.com", "TAX-1"));

    verify(pendingSignups)
        .replaceIfUnderLimit(
            eq(winner.getId()), eq("TAX-1"), any(), any(), any(), any(), any(), anyInt());
    verify(events).publish(any(SignupVerificationRequestedEvent.class));
  }

  @Test
  @DisplayName("losing a race on the tax number is a refusal")
  void raceOnTheTaxNumber() {
    when(pendingSignups.findByEmailAndCompletedAtIsNull("owner@example.com"))
        .thenReturn(Optional.empty());
    when(pendingSignups.insertIfAbsent(any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(0);

    assertThatThrownBy(() -> service.request(form("owner@example.com", "TAX-1")))
        .isInstanceOf(TaxIdAlreadyExistsException.class);

    verifyNoInteractions(evidence, events);
  }

  private DomainEvent singleEvent() {
    var published = ArgumentCaptor.forClass(DomainEvent.class);
    verify(events).publish(published.capture());
    return published.getValue();
  }

  private static String tokenIn(String setupUrl) {
    assertThat(setupUrl).startsWith("https://app.test/setup?token=");
    return setupUrl.substring(setupUrl.indexOf('=') + 1);
  }

  private static SelfSignupRequest form(String email, String taxId) {
    return SelfSignupRequest.builder()
        .organizationName("Hawthorn Weaving Ltd")
        .taxId(taxId)
        .organizationType(OrganizationType.VERTICAL_MILL)
        .firstName("Eleanor")
        .lastName("Hawthorn")
        .email(email)
        .intent(SignupIntent.PLAYGROUND)
        .acceptedTerms(true)
        .build();
  }

  private static PendingSignup openSignup(String email, String taxId, int sendCount) {
    PendingSignup row = BeanUtils.instantiateClass(PendingSignup.class);
    ReflectionTestUtils.setField(row, "id", UUID.randomUUID());
    ReflectionTestUtils.setField(row, "email", email);
    ReflectionTestUtils.setField(row, "taxId", taxId);
    ReflectionTestUtils.setField(
        row,
        "request",
        new PendingSignupRequest(
            "Old Name", OrganizationType.WEAVER, "Old", "Name", null, "PLAYGROUND"));
    ReflectionTestUtils.setField(row, "tokenHash", SignupLinkToken.issue().hash());
    ReflectionTestUtils.setField(row, "termsAcceptedAt", Instant.now().minusSeconds(3600));
    ReflectionTestUtils.setField(row, "expiresAt", Instant.now().plus(Duration.ofHours(1)));
    ReflectionTestUtils.setField(row, "sendCount", sendCount);
    return row;
  }
}
