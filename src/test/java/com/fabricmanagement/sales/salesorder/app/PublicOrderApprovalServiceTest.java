package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.SystemTransactionExecutor;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.sales.common.exception.OrderDomainException;
import com.fabricmanagement.sales.salesorder.domain.CustomerApproval;
import com.fabricmanagement.sales.salesorder.domain.CustomerApprovalChannel;
import com.fabricmanagement.sales.salesorder.domain.OrderVersion;
import com.fabricmanagement.sales.salesorder.domain.OrderVersionContent;
import com.fabricmanagement.sales.salesorder.domain.OrderVersionKind;
import com.fabricmanagement.sales.salesorder.dto.PublicOrderApprovalDtos;
import com.fabricmanagement.sales.salesorder.infra.repository.CustomerApprovalRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderVersionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/** The customer's page behind the link: nothing without the code, no decision without it. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PublicOrderApprovalServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-02T09:00:00Z");
  private static final UUID TENANT = UUID.randomUUID();
  private static final String TOKEN = "0123456789abcdef".repeat(4);

  @Mock private CustomerApprovalRepository approvals;
  @Mock private OrderVersionRepository versions;
  @Mock private SystemTransactionExecutor system;
  @Mock private TransactionTemplate transactions;
  @Mock private CustomerApprovalDecisionService decisions;
  @Mock private CustomerApprovalMailer mailer;
  @Mock private ApproverAuthorities approvers;

  private PublicOrderApprovalService service;
  private CustomerApproval approval;
  private UUID tenantSeen;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    service =
        new PublicOrderApprovalService(
            approvals,
            versions,
            system,
            transactions,
            decisions,
            mailer,
            Clock.fixed(NOW, ZoneOffset.UTC),
            approvers);
    when(approvers.isValid(any(), any())).thenReturn(true);
    when(transactions.execute(any()))
        .thenAnswer(
            invocation -> {
              tenantSeen = TenantContext.getCurrentTenantIdOrNull();
              return ((TransactionCallback<Object>) invocation.getArgument(0))
                  .doInTransaction(null);
            });
    String hash = OrderVersionSnapshotter.sha256(TOKEN);
    when(system.executeQuery(any(), any(RowMapper.class), eq(hash))).thenReturn(List.of(TENANT));
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
            "f".repeat(64),
            UUID.randomUUID(),
            NOW.minusSeconds(60));
    ReflectionTestUtils.setField(version, "id", UUID.randomUUID());
    approval =
        CustomerApproval.request(
            version,
            UUID.randomUUID(),
            "Jane Smith",
            "jane@example.co.uk",
            NOW.plus(Duration.ofDays(3)),
            null,
            UUID.randomUUID(),
            NOW.minusSeconds(60));
    ReflectionTestUtils.setField(approval, "id", UUID.randomUUID());
    approval.issueLink(hash, UUID.randomUUID(), NOW.minusSeconds(60));
    when(approvals.findByTenantIdAndTokenHash(TENANT, hash)).thenReturn(Optional.of(approval));
    when(versions.findByTenantIdAndId(TENANT, version.getId())).thenReturn(Optional.of(version));
  }

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  private String sendAndCaptureCode() {
    service.sendCode(TOKEN);
    ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
    verify(mailer)
        .sendCode(
            eq(TENANT), eq("jane@example.co.uk"), eq("SO-1"), eq("Bradford Mills"), code.capture());
    return code.getValue();
  }

  @Test
  void theLinkAloneShowsWhoAsksButNoContent() {
    PublicOrderApprovalDtos.LinkView view = service.view(TOKEN);

    assertThat(view.state()).isEqualTo(PublicOrderApprovalDtos.LinkState.OPEN);
    assertThat(view.orderNumber()).isEqualTo("SO-1");
    assertThat(view.sellerName()).isEqualTo("Bradford Mills");
    assertThat(view.recipientEmailMasked()).isEqualTo("j***@example.co.uk");
    // The link's tenant was set before any transaction opened.
    assertThat(tenantSeen).isEqualTo(TENANT);
    assertThat(TenantContext.getCurrentTenantIdOrNull()).isNull();
  }

  @Test
  void anUnknownOrMalformedLinkIsNotFound() {
    assertThatThrownBy(() -> service.view("not-a-token")).isInstanceOf(NotFoundException.class);
    assertThatThrownBy(() -> service.view("f".repeat(64))).isInstanceOf(NotFoundException.class);
    verifyNoInteractions(transactions);
  }

  @Test
  void theRightCodeOpensTheVersionAndStartsASession() {
    String code = sendAndCaptureCode();
    assertThat(code).matches("\\d{6}");

    PublicOrderApprovalDtos.Verified verified = service.verify(TOKEN, code);

    assertThat(verified.version().content().orderNumber()).isEqualTo("SO-1");
    assertThat(approval.hasSession(OrderVersionSnapshotter.sha256(verified.session()), NOW))
        .isTrue();
    assertThat(service.version(TOKEN, verified.session()).versionNo()).isEqualTo(1);
  }

  @Test
  void aWrongCodeIsRefusedAndCounted() {
    String code = sendAndCaptureCode();
    String wrong = code.equals("000000") ? "000001" : "000000";

    assertThatThrownBy(() -> service.verify(TOKEN, wrong))
        .isInstanceOfSatisfying(
            OrderDomainException.class,
            exception -> {
              assertThat(exception.getErrorCode()).isEqualTo("CODE_INVALID");
              assertThat(exception.getMessage()).contains("4 attempts left");
            });
    assertThat(approval.getCodeAttempts()).isEqualTo(1);
    verify(approvals, org.mockito.Mockito.atLeast(2)).save(approval);
  }

  /** The representative's authority ended after the link went out (ADR-0014 D4). */
  private void authorityEnded() {
    authorityNoLongerInForce(ApproverAuthorities.AUTHORITY_INACTIVE);
  }

  /** The authority stopped being in force for {@code reason}, after the link went out. */
  private void authorityNoLongerInForce(String reason) {
    when(approvers.isValid(any(), any())).thenReturn(false);
    org.mockito.Mockito.doThrow(ApproverAuthorities.refusal(reason))
        .when(approvers)
        .requireValid(any(), any());
    org.mockito.Mockito.doThrow(ApproverAuthorities.refusal(reason))
        .when(approvers)
        .holdValid(any(), any());
  }

  @Test
  void everyStepOfTheCustomerHoldsTheAuthority() {
    String code = sendAndCaptureCode();
    PublicOrderApprovalDtos.Verified verified = service.verify(TOKEN, code);
    service.version(TOKEN, verified.session());

    // Sending the code, checking it and showing the content each hold the authority's lock, as a
    // decision does; none relies on a read that an ending could overtake.
    verify(approvers, org.mockito.Mockito.times(3)).holdValid(any(), any());
    verify(approvers, never()).requireValid(any(), any());
  }

  @Test
  void aLinkWhoseApproverAddressChangedOnTheCardIsClosedAndSendsNoCode() {
    authorityNoLongerInForce(ApproverAuthorities.APPROVER_EMAIL_CHANGED);

    assertThat(service.view(TOKEN).state()).isEqualTo(PublicOrderApprovalDtos.LinkState.CLOSED);
    assertThatThrownBy(() -> service.sendCode(TOKEN))
        .isInstanceOfSatisfying(
            OrderDomainException.class,
            exception ->
                assertThat(exception.getErrorCode())
                    .isEqualTo(ApproverAuthorities.APPROVER_EMAIL_CHANGED));
    verify(mailer, never()).sendCode(any(), any(), any(), any(), any());
  }

  @Test
  void aLinkWhoseAuthorityEndedIsClosedAndSendsNoCode() {
    authorityEnded();

    assertThat(service.view(TOKEN).state()).isEqualTo(PublicOrderApprovalDtos.LinkState.CLOSED);
    assertThat(service.view(TOKEN).nextCodeAllowedAt()).isNull();
    assertThatThrownBy(() -> service.sendCode(TOKEN))
        .isInstanceOfSatisfying(
            OrderDomainException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo("APPROVAL_AUTHORITY_INACTIVE"));
    verify(mailer, never()).sendCode(any(), any(), any(), any(), any());
  }

  @Test
  void aCodeReceivedBeforeTheAuthorityEndedOpensNothing() {
    String code = sendAndCaptureCode();
    authorityEnded();

    assertThatThrownBy(() -> service.verify(TOKEN, code))
        .isInstanceOfSatisfying(
            OrderDomainException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo("APPROVAL_AUTHORITY_INACTIVE"));
    assertThat(approval.getSessionHash()).isNull();
  }

  @Test
  void aSessionStartedBeforeTheAuthorityEndedShowsNoContent() {
    String code = sendAndCaptureCode();
    PublicOrderApprovalDtos.Verified verified = service.verify(TOKEN, code);
    authorityEnded();

    assertThatThrownBy(() -> service.version(TOKEN, verified.session()))
        .isInstanceOfSatisfying(
            OrderDomainException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo("APPROVAL_AUTHORITY_INACTIVE"));
  }

  @Test
  void theContentStaysClosedWithoutTheSession() {
    assertThatThrownBy(() -> service.version(TOKEN, "made-up"))
        .isInstanceOfSatisfying(
            OrderDomainException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo("SESSION_REQUIRED"));
  }

  @Test
  void theDecisionNeedsTheVerifiedSessionOfTheRecipient() {
    String session = service.verify(TOKEN, sendAndCaptureCode()).session();
    ArgumentCaptor<CustomerApproval.Decider> decider =
        ArgumentCaptor.forClass(CustomerApproval.Decider.class);
    ArgumentCaptor<CustomerApprovalDecisionService.Authority> authority =
        ArgumentCaptor.forClass(CustomerApprovalDecisionService.Authority.class);

    service.approve(TOKEN, session, "203.0.113.7", "Mozilla/5.0");

    verify(decisions).approve(eq(approval.getId()), decider.capture(), authority.capture());
    assertThat(decider.getValue().channel()).isEqualTo(CustomerApprovalChannel.EMAIL_LINK);
    assertThat(decider.getValue().email()).isEqualTo("jane@example.co.uk");
    assertThat(decider.getValue().ipAddress()).isEqualTo("203.0.113.7");
    authority.getValue().check(approval, NOW);
    CustomerApprovalDecisionService.Authority forged = captureAuthority("another-session");
    assertThatThrownBy(() -> forged.check(approval, NOW))
        .isInstanceOfSatisfying(
            OrderDomainException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo("SESSION_REQUIRED"));
  }

  @Test
  void aChangeRequestCarriesTheNote() {
    service.requestChanges(TOKEN, "session", "Earlier please", null, null);

    verify(decisions).requestChanges(eq(approval.getId()), any(), eq("Earlier please"), any());
    verify(decisions, never()).approve(any(), any(), any());
  }

  private CustomerApprovalDecisionService.Authority captureAuthority(String session) {
    org.mockito.Mockito.clearInvocations(decisions);
    service.approve(TOKEN, session, null, null);
    ArgumentCaptor<CustomerApprovalDecisionService.Authority> authority =
        ArgumentCaptor.forClass(CustomerApprovalDecisionService.Authority.class);
    verify(decisions).approve(any(), any(), authority.capture());
    return authority.getValue();
  }
}
