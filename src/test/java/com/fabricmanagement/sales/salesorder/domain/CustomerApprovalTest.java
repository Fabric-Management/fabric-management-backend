package com.fabricmanagement.sales.salesorder.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.sales.common.exception.OrderDomainException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CustomerApprovalTest {

  private static final Instant NOW = Instant.parse("2026-10-02T09:00:00Z");
  private static final UUID SALES = UUID.randomUUID();
  private static final String HASH = "a".repeat(64);
  private static final UUID AUTHORITY = UUID.randomUUID();

  private static OrderVersion version() {
    OrderVersion version =
        OrderVersion.freeze(
            UUID.randomUUID(),
            null,
            OrderVersionKind.APPROVAL,
            1,
            0,
            UUID.randomUUID(),
            new OrderVersionContent(
                "Bradford Mills",
                "SO-1",
                "Northern Garments",
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                0,
                List.of(),
                null),
            "b".repeat(64),
            SALES,
            NOW);
    ReflectionTestUtils.setField(version, "id", UUID.randomUUID());
    return version;
  }

  private static CustomerApproval sent(Instant proposalValidUntil, Integer hours) {
    CustomerApproval approval =
        CustomerApproval.request(
            version(),
            AUTHORITY,
            "Jane Smith",
            "jane@example.co.uk",
            proposalValidUntil,
            hours,
            SALES,
            NOW);
    ReflectionTestUtils.setField(approval, "id", UUID.randomUUID());
    approval.issueLink(HASH, SALES, NOW);
    return approval;
  }

  private static CustomerApproval.Decider jane() {
    return new CustomerApproval.Decider(
        CustomerApprovalChannel.EMAIL_LINK, "Jane Smith", "jane@example.co.uk", null, null, null);
  }

  @Test
  void theLinkLastsFortyEightHoursByDefault() {
    CustomerApproval approval = sent(NOW.plus(Duration.ofDays(10)), null);

    assertThat(approval.getStatus()).isEqualTo(CustomerApprovalStatus.SENT);
    assertThat(approval.getLinkExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(48)));
  }

  @Test
  void theLinkNeverOutlivesPlanningsProposal() {
    Instant proposalEnd = NOW.plus(Duration.ofHours(5));

    CustomerApproval approval = sent(proposalEnd, 72);

    assertThat(approval.getLinkExpiresAt()).isEqualTo(proposalEnd);
    assertThat(approval.isOpenAt(proposalEnd)).isFalse();
  }

  @Test
  void aRequestNamesTheAuthorityItIsSentUnder() {
    assertThatThrownBy(
            () ->
                CustomerApproval.request(
                    version(),
                    null,
                    "Jane",
                    "jane@example.co.uk",
                    NOW.plusSeconds(3600),
                    null,
                    SALES,
                    NOW))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(sent(NOW.plus(Duration.ofDays(3)), null).getApprovalAuthorityId())
        .isEqualTo(AUTHORITY);
  }

  @Test
  void anExpiredProposalIsNeverSent() {
    assertThatThrownBy(
            () ->
                CustomerApproval.request(
                    version(), AUTHORITY, "Jane", "jane@example.co.uk", NOW, null, SALES, NOW))
        .isInstanceOfSatisfying(
            OrderDomainException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo("PROPOSAL_EXPIRED"));
  }

  @Test
  void theContactNeedsAnEmailAddress() {
    assertThatThrownBy(
            () ->
                CustomerApproval.request(
                    version(),
                    AUTHORITY,
                    "Jane",
                    "not-an-address",
                    NOW.plusSeconds(3600),
                    null,
                    SALES,
                    NOW))
        .isInstanceOfSatisfying(
            OrderDomainException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo("CONTACT_EMAIL_REQUIRED"));
  }

  @Test
  void aVersionWaitingForTheInternalApprovalIsNotSentBeforeIt() {
    CustomerApproval approval =
        CustomerApproval.request(
            version(),
            AUTHORITY,
            "Jane",
            "jane@example.co.uk",
            NOW.plusSeconds(86_400),
            null,
            SALES,
            NOW);
    approval.awaitInternalApproval(UUID.randomUUID());

    assertThatThrownBy(() -> approval.issueLink(HASH, SALES, NOW))
        .isInstanceOfSatisfying(
            OrderDomainException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo("AWAITING_INTERNAL_APPROVAL"));

    approval.internallyApproved(NOW);
    approval.issueLink(HASH, SALES, NOW);
    assertThat(approval.getStatus()).isEqualTo(CustomerApprovalStatus.SENT);
  }

  @Test
  void aWrongCodeIsCountedAndTheCodeLocksAtTheLimit() {
    CustomerApproval approval = sent(NOW.plus(Duration.ofDays(3)), null);
    approval.codeIssued("c".repeat(64), NOW);

    for (int attempt = 0; attempt < CustomerApproval.MAX_CODE_ATTEMPTS; attempt++) {
      assertThat(approval.verifyCode("d".repeat(64), "e".repeat(64), NOW)).isFalse();
    }

    assertThat(approval.codeAttemptsLeft()).isZero();
    assertThatThrownBy(() -> approval.verifyCode("c".repeat(64), "e".repeat(64), NOW))
        .isInstanceOfSatisfying(
            OrderDomainException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo("CODE_LOCKED"));
  }

  @Test
  void theRightCodeStartsASessionAndCannotBeReused() {
    CustomerApproval approval = sent(NOW.plus(Duration.ofDays(3)), null);
    approval.codeIssued("c".repeat(64), NOW);

    assertThat(approval.verifyCode("c".repeat(64), "e".repeat(64), NOW)).isTrue();

    assertThat(approval.hasSession("e".repeat(64), NOW.plusSeconds(60))).isTrue();
    assertThat(approval.hasSession("e".repeat(64), NOW.plus(Duration.ofMinutes(31)))).isFalse();
    assertThatThrownBy(() -> approval.verifyCode("c".repeat(64), "f".repeat(64), NOW))
        .isInstanceOfSatisfying(
            OrderDomainException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo("CODE_EXPIRED"));
  }

  @Test
  void codesAreRateLimitedPerLink() {
    CustomerApproval approval = sent(NOW.plus(Duration.ofDays(3)), null);
    approval.codeIssued("c".repeat(64), NOW);

    assertThatThrownBy(() -> approval.codeIssued("c".repeat(64), NOW.plusSeconds(10)))
        .isInstanceOfSatisfying(
            OrderDomainException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo("CODE_RECENTLY_SENT"));
    Instant later = NOW;
    for (int sent = 1; sent < CustomerApproval.MAX_CODES; sent++) {
      later = later.plusSeconds(61);
      approval.codeIssued("c".repeat(64), later);
    }
    Instant afterLimit = later.plusSeconds(61);
    assertThatThrownBy(() -> approval.codeIssued("c".repeat(64), afterLimit))
        .isInstanceOfSatisfying(
            OrderDomainException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo("TOO_MANY_CODES"));
  }

  @Test
  void aResendReplacesTheLinkAndEndsTheEarlierSession() {
    CustomerApproval approval = sent(NOW.plus(Duration.ofDays(3)), null);
    approval.codeIssued("c".repeat(64), NOW);
    approval.verifyCode("c".repeat(64), "e".repeat(64), NOW);

    String address = approval.getRecipientEmail();

    // The same representative, at the address their authority was granted for: nothing else.
    approval.issueLink("9".repeat(64), SALES, NOW);

    assertThat(approval.getTokenHash()).isEqualTo("9".repeat(64));
    assertThat(approval.getRecipientName()).isEqualTo("Jane Smith");
    assertThat(approval.getRecipientEmail()).isEqualTo(address);
    assertThat(approval.getApprovalAuthorityId()).isEqualTo(AUTHORITY);
    assertThat(approval.getLinksIssued()).isEqualTo(2);
    assertThat(approval.hasSession("e".repeat(64), NOW)).isFalse();
  }

  @Test
  void aDecisionClosesTheLinkForAnyFurtherDecision() {
    CustomerApproval approval = sent(NOW.plus(Duration.ofDays(3)), null);

    approval.approved(jane(), NOW);

    assertThat(approval.getStatus()).isEqualTo(CustomerApprovalStatus.APPROVED);
    assertThat(approval.getDecidedByEmail()).isEqualTo("jane@example.co.uk");
    assertThatThrownBy(() -> approval.changesRequested(jane(), "Cheaper please", NOW))
        .isInstanceOfSatisfying(
            OrderDomainException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo("APPROVAL_CLOSED"));
  }

  @Test
  void aChangeRequestNeedsItsNoteAndStaysOpenUntilSalesFollowsItUp() {
    CustomerApproval approval = sent(NOW.plus(Duration.ofDays(3)), null);
    assertThatThrownBy(() -> approval.changesRequested(jane(), "  ", NOW))
        .isInstanceOf(OrderDomainException.class);

    approval.changesRequested(jane(), "Can you bring the date forward?", NOW);

    assertThat(approval.hasOpenChangeRequest()).isTrue();
    approval.changesResolved(NOW.plusSeconds(60));
    assertThat(approval.hasOpenChangeRequest()).isFalse();
  }

  @Test
  void anExpiredLinkTakesNoDecision() {
    CustomerApproval approval = sent(NOW.plus(Duration.ofDays(3)), 1);

    assertThatThrownBy(() -> approval.approved(jane(), NOW.plus(Duration.ofHours(2))))
        .isInstanceOfSatisfying(
            OrderDomainException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo("LINK_EXPIRED"));
  }

  @Test
  void aWithdrawnRequestStopsTheLinkAndKeepsTheReason() {
    CustomerApproval approval = sent(NOW.plus(Duration.ofDays(3)), null);

    approval.withdraw("Planning reopened the evaluation", SALES, NOW);

    assertThat(approval.getStatus()).isEqualTo(CustomerApprovalStatus.WITHDRAWN);
    assertThat(approval.getClosedReason()).isEqualTo("Planning reopened the evaluation");
    assertThat(approval.isOpenAt(NOW)).isFalse();
  }
}
