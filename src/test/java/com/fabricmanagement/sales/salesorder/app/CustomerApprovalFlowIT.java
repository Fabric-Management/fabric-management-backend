package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.common.infrastructure.security.PermissionEvaluator;
import com.fabricmanagement.common.infrastructure.security.dto.PermissionResult;
import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.platform.communication.domain.ContactType;
import com.fabricmanagement.platform.organization.domain.Organization;
import com.fabricmanagement.platform.organization.domain.OrganizationType;
import com.fabricmanagement.platform.organization.dto.EditOrganizationContactRequest;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationRepository;
import com.fabricmanagement.platform.tenant.domain.Tenant;
import com.fabricmanagement.platform.tenant.infra.repository.TenantRepository;
import com.fabricmanagement.platform.tradingpartner.domain.PartnerType;
import com.fabricmanagement.platform.tradingpartner.domain.TradingPartner;
import com.fabricmanagement.platform.tradingpartner.domain.TradingPartnerRegistry;
import com.fabricmanagement.platform.tradingpartner.infra.repository.TradingPartnerRegistryRepository;
import com.fabricmanagement.platform.tradingpartner.infra.repository.TradingPartnerRepository;
import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.platform.user.domain.Role;
import com.fabricmanagement.platform.user.domain.SystemUser;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.infra.repository.RoleRepository;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import com.fabricmanagement.sales.common.exception.OrderDomainException;
import com.fabricmanagement.sales.salesorder.domain.ApprovalAuthorityBasis;
import com.fabricmanagement.sales.salesorder.domain.CustomerApproval;
import com.fabricmanagement.sales.salesorder.domain.CustomerApprovalChannel;
import com.fabricmanagement.sales.salesorder.domain.CustomerApprovalStatus;
import com.fabricmanagement.sales.salesorder.domain.DeliveryProposal;
import com.fabricmanagement.sales.salesorder.domain.DeliveryTerm;
import com.fabricmanagement.sales.salesorder.domain.DeliveryTerms;
import com.fabricmanagement.sales.salesorder.domain.IncotermsVersion;
import com.fabricmanagement.sales.salesorder.domain.LineShipmentPreference;
import com.fabricmanagement.sales.salesorder.domain.OrderStatus;
import com.fabricmanagement.sales.salesorder.domain.OrderVersionContent;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import com.fabricmanagement.sales.salesorder.dto.CustomerApprovalDtos;
import com.fabricmanagement.sales.salesorder.dto.OrderPartyDtos.ApprovalAuthorityView;
import com.fabricmanagement.sales.salesorder.dto.OrderPartyDtos.GrantApprovalAuthorityRequest;
import com.fabricmanagement.sales.salesorder.dto.OrderPartyDtos.RevokeApprovalAuthorityRequest;
import com.fabricmanagement.sales.salesorder.dto.PublicOrderApprovalDtos;
import com.fabricmanagement.sales.salesorder.infra.repository.DeliveryProposalRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderLineRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderRepository;
import com.fabricmanagement.testsupport.PostgresImage;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The customer's approval end to end, on the real schema: sales sends the planned order, the
 * customer opens the link with no tenant context, verifies the e-mailed code and approves. The
 * order is confirmed once, with the agreed date and term; a change request sends it back to sales;
 * a withdrawn link decides nothing.
 *
 * <p>The approval authority in force (ADR-0014 OD-13): a revoked or expired authority, or one whose
 * contact point was edited to another address, decides nothing; a revocation and a decision that
 * overlap are put one after the other by the authority's row lock.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DisabledIf(value = "dockerNotAvailable", disabledReason = "Docker is not available")
class CustomerApprovalFlowIT {

  @Container
  @SuppressWarnings("resource")
  static PostgreSQLContainer<?> postgres =
      PostgresImage.container()
          .withDatabaseName("customer_approval_test")
          .withUsername("test")
          .withPassword("test");

  @DynamicPropertySource
  static void configureDatasource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.flyway.url", postgres::getJdbcUrl);
    registry.add("spring.flyway.user", postgres::getUsername);
    registry.add("spring.flyway.password", postgres::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
  }

  static boolean dockerNotAvailable() {
    return !org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
  }

  private static final LocalDate PROPOSED = LocalDate.now().plusDays(40);

  @Autowired private TenantRepository tenants;
  @Autowired private OrganizationRepository organizations;
  @Autowired private RoleRepository roles;
  @Autowired private UserRepository users;
  @Autowired private TradingPartnerRegistryRepository registries;
  @Autowired private TradingPartnerRepository partners;
  @Autowired private SalesOrderRepository orders;
  @Autowired private SalesOrderLineRepository lines;
  @Autowired private DeliveryProposalRepository proposals;
  @Autowired private CustomerApprovalService customerApprovals;
  @Autowired private PublicOrderApprovalService publicApprovals;
  @Autowired private OrderApprovalInvalidator invalidator;
  @Autowired private TransactionTemplate transactions;
  @Autowired private JdbcTemplate jdbc;

  @Autowired
  private com.fabricmanagement.platform.organization.api.facade.OrganizationContactFacade
      organizationContacts;

  @Autowired private ApprovalAuthorityService authorityService;
  @Autowired private CustomerApprovalDecisionService decisions;

  @MockitoBean private PermissionEvaluator permissionEvaluator;
  @MockitoBean private CustomerApprovalMailer mailer;

  private UUID tenantId;
  private UUID sales;
  private UUID orderId;
  private UUID partnerId;
  private UUID customerOrganizationId;
  private UUID janesAddress;
  private UUID authorityId;

  @BeforeEach
  void setUp() {
    PermissionResult salesTeam =
        new PermissionResult(
            Map.of(
                "sales", Map.of("read", DataScope.ORGANIZATION, "write", DataScope.ORGANIZATION)),
            false);
    when(permissionEvaluator.evaluate(any(), any(), any(), any())).thenReturn(salesTeam);
    when(permissionEvaluator.evaluateFresh(any(), any(), any(), any())).thenReturn(salesTeam);

    String suffix = UUID.randomUUID().toString().substring(0, 8);
    tenantId = tenants.save(Tenant.create("Bradford Mills " + suffix, "CA-" + suffix)).getId();
    TenantContext.setCurrentTenantId(tenantId);
    Organization organization =
        organizations.save(
            Organization.create(
                "Bradford Mills " + suffix, "TAX-" + suffix, OrganizationType.WEAVER));
    Role role = roles.save(Role.create("Sales " + suffix, "WORKER", "Approval test"));
    User user = User.create("Sam", "Seller", organization.getId());
    user.setRole(role);
    sales = users.save(user).getId();
    TenantContext.setCurrentUserId(sales);

    TradingPartnerRegistry registry =
        TradingPartnerRegistry.create(null, "Northern Garments " + suffix, "GBR");
    registry.setUid("REG-" + UUID.randomUUID());
    // The customer's organisation with Jane's e-mail address on its card.
    Organization customerOrganization =
        organizations.save(
            Organization.create(
                "Northern Garments " + suffix,
                "TAXC-" + suffix,
                OrganizationType.EXTERNAL_PARTNER));
    TradingPartner customer =
        TradingPartner.create(registries.save(registry), PartnerType.CUSTOMER, "Northern Garments");
    customerOrganizationId = customerOrganization.getId();
    customer.setOrganizationId(customerOrganizationId);
    partnerId = partners.saveAndFlush(customer).getId();
    janesAddress =
        organizationContacts
            .createAndAssignContact(
                customerOrganization.getId(),
                com.fabricmanagement.platform.communication.dto.CreateContactRequest.builder()
                    .contactValue("jane@northern-garments.co.uk")
                    .contactType(ContactType.EMAIL)
                    .label("Jane Smith")
                    .isPersonal(false)
                    .build(),
                true,
                "Buying")
            .getContactId();
    // Jane may approve orders for the customer (ADR-0014 D4).
    authorityId = grantJane();
    SalesOrder order =
        SalesOrder.builder()
            .tradingPartnerId(partnerId)
            .orderNumber("SO-" + suffix)
            .status(OrderStatus.DRAFT)
            .orderDate(LocalDate.now())
            // The order's day-to-day contact is not the approver: the request goes to Jane.
            .contactName("Order desk")
            .contactEmail("orders@northern-garments.co.uk")
            .approverContactId(janesAddress)
            .build();
    order.applyDeliveryTerms(
        DeliveryTerms.of(DeliveryTerm.FCA, "Felixstowe", IncotermsVersion.INCOTERMS_2020));
    order.applyDeliveryTermStatus(null, null);
    orderId = orders.saveAndFlush(order).getId();
    lines.saveAndFlush(
        SalesOrderLine.builder()
            .salesOrderId(orderId)
            .productId(UUID.randomUUID())
            .productDesc("Cotton poplin 140 cm")
            .requestedQty(new BigDecimal("1200"))
            .unit("MT")
            .unitPrice(Money.of(new BigDecimal("4.20"), "GBP"))
            .build());
    // Planning finished with a proposal valid for a week.
    jdbc.update(
        "UPDATE sales_ord.sales_order SET flow_stage = 'PLANNED', planning_round = 1"
            + " WHERE tenant_id = ? AND id = ?",
        tenantId,
        orderId);
    proposals.saveAndFlush(
        DeliveryProposal.propose(
            orderId,
            null,
            1,
            0,
            PROPOSED,
            Instant.now().plus(Duration.ofDays(7)),
            order.getDeliveryTerms(),
            null,
            sales,
            Instant.now(),
            LocalDate.now()));
    TenantContext.clear();
  }

  @AfterEach
  void clear() {
    TenantContext.clear();
  }

  @Test
  void theCustomerApprovesTheSentVersionThroughTheLinkAndTheOrderIsConfirmedOnce() {
    String token = send();

    // The customer has no tenant context: the link alone finds the request.
    PublicOrderApprovalDtos.LinkView link = publicApprovals.view(token);
    assertThat(link.state()).isEqualTo(PublicOrderApprovalDtos.LinkState.OPEN);
    assertThat(link.recipientEmailMasked()).isEqualTo("j***@northern-garments.co.uk");
    PublicOrderApprovalDtos.Verified verified = publicApprovals.verify(token, code(token));
    OrderVersionContent content = verified.version().content();
    assertThat(content.lines())
        .singleElement()
        .satisfies(
            line -> {
              assertThat(line.product()).isEqualTo("Cotton poplin 140 cm");
              assertThat(line.unitPrice()).isEqualByComparingTo("4.20");
            });
    assertThat(content.proposal().proposedOn()).isEqualTo(PROPOSED);

    PublicOrderApprovalDtos.LinkView approved =
        publicApprovals.approve(token, verified.session(), "203.0.113.7", "JUnit");
    PublicOrderApprovalDtos.LinkView again =
        publicApprovals.approve(token, verified.session(), "203.0.113.7", "JUnit");

    assertThat(approved.state()).isEqualTo(PublicOrderApprovalDtos.LinkState.APPROVED);
    assertThat(again.state()).isEqualTo(PublicOrderApprovalDtos.LinkState.APPROVED);
    Map<String, Object> row =
        jdbc.queryForMap(
            "SELECT status, flow_stage, delivery_term_status, committed_on"
                + " FROM sales_ord.sales_order WHERE id = ?",
            orderId);
    assertThat(row.get("status")).isEqualTo("CONFIRMED");
    assertThat(row.get("flow_stage")).isEqualTo("CUSTOMER_APPROVED");
    assertThat(row.get("delivery_term_status")).isEqualTo("AGREED_BY_CUSTOMER");
    assertThat(((java.sql.Date) row.get("committed_on")).toLocalDate()).isEqualTo(PROPOSED);
    assertThat(
            jdbc.queryForList(
                "SELECT origin, channel FROM sales_ord.delivery_commitment"
                    + " WHERE sales_order_id = ?",
                orderId))
        .singleElement()
        .satisfies(
            commitment -> {
              assertThat(commitment.get("origin")).isEqualTo("INITIAL");
              assertThat(commitment.get("channel")).isEqualTo("APPROVAL_LINK");
            });
    assertThat(
            jdbc.queryForMap(
                "SELECT status, decided_by_email, ip_address FROM sales_ord.customer_approval"
                    + " WHERE sales_order_id = ?",
                orderId))
        .containsEntry("status", "APPROVED")
        .containsEntry("decided_by_email", "jane@northern-garments.co.uk")
        .containsEntry("ip_address", "203.0.113.7");
    // Only hashes are stored: the link itself appears nowhere in the table.
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM sales_ord.customer_approval WHERE token_hash = ?",
                Integer.class,
                token))
        .isZero();
  }

  @Test
  void theSentVersionFreezesEachLinesPreferencesWhenTheOrderChangesLater() {
    // LINE-PREFERENCES-1: a second line with the non-default choices, beside the default one.
    TenantContext.setCurrentTenantId(tenantId);
    TenantContext.setCurrentUserId(sales);
    UUID twill;
    try {
      twill =
          lines
              .saveAndFlush(
                  SalesOrderLine.builder()
                      .salesOrderId(orderId)
                      .productId(UUID.randomUUID())
                      .productDesc("Twill 240 cm")
                      .requestedQty(new BigDecimal("800"))
                      .unit("MT")
                      .unitPrice(Money.of(new BigDecimal("5.10"), "GBP"))
                      .singleLotRequired(true)
                      .shipmentPreference(LineShipmentPreference.WHEN_COMPLETE)
                      .build())
              .getId();
    } finally {
      TenantContext.clear();
    }
    String token = send();

    // After sending, both lines' preferences change on the order itself.
    jdbc.update(
        "UPDATE sales_ord.sales_order_line SET single_lot_required = (id <> ?),"
            + " shipment_preference = CASE WHEN id = ? THEN 'AS_READY' ELSE 'WHEN_COMPLETE' END"
            + " WHERE sales_order_id = ?",
        twill,
        twill,
        orderId);

    OrderVersionContent content = publicApprovals.verify(token, code(token)).version().content();
    assertThat(content.lines())
        .extracting(
            OrderVersionContent.Line::product,
            OrderVersionContent.Line::singleLotRequired,
            OrderVersionContent.Line::shipmentPreference)
        .containsExactlyInAnyOrder(
            tuple("Cotton poplin 140 cm", false, LineShipmentPreference.AS_READY),
            tuple("Twill 240 cm", true, LineShipmentPreference.WHEN_COMPLETE));
    // The order itself did change: the version is a frozen copy, not a view of it.
    assertThat(
            jdbc.queryForObject(
                "SELECT shipment_preference FROM sales_ord.sales_order_line WHERE id = ?",
                String.class,
                twill))
        .isEqualTo("AS_READY");
  }

  @Test
  void aChangeRequestSendsTheOrderBackToSalesAsOpenWork() {
    String token = send();
    String session = publicApprovals.verify(token, code(token)).session();

    PublicOrderApprovalDtos.LinkView view =
        publicApprovals.requestChanges(
            token, session, "Could you deliver a week earlier?", null, null);

    assertThat(view.state()).isEqualTo(PublicOrderApprovalDtos.LinkState.CHANGES_REQUESTED);
    assertThat(
            jdbc.queryForMap(
                "SELECT status, flow_stage FROM sales_ord.sales_order WHERE id = ?", orderId))
        .containsEntry("status", "DRAFT")
        .containsEntry("flow_stage", "DRAFT");
    TenantContext.setCurrentTenantId(tenantId);
    TenantContext.setCurrentUserId(sales);
    assertThat(customerApprovals.changeRequests(sales))
        .singleElement()
        .satisfies(
            item -> {
              assertThat(item.orderId()).isEqualTo(orderId);
              assertThat(item.note()).isEqualTo("Could you deliver a week earlier?");
            });
  }

  @Test
  void aWithdrawnLinkDecidesNothing() {
    String token = send();
    String session = publicApprovals.verify(token, code(token)).session();
    TenantContext.setCurrentTenantId(tenantId);
    TenantContext.setCurrentUserId(sales);
    transactions.executeWithoutResult(
        status -> invalidator.withdrawOpen(orderId, "Planning reopened the evaluation", sales));
    TenantContext.clear();

    assertThat(publicApprovals.view(token).state())
        .isEqualTo(PublicOrderApprovalDtos.LinkState.CLOSED);
    assertThatThrownBy(() -> publicApprovals.approve(token, session, null, null))
        .isInstanceOfSatisfying(
            OrderDomainException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo("APPROVAL_CLOSED"));
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM sales_ord.sales_order WHERE id = ?", String.class, orderId))
        .isEqualTo("DRAFT");
  }

  @Test
  void aRevokedAuthorityStopsTheLinkTheCodeAndTheSessionAlreadyGiven() {
    String token = send();
    String session = publicApprovals.verify(token, code(token)).session();
    revokeJane();

    assertThat(publicApprovals.view(token).state())
        .isEqualTo(PublicOrderApprovalDtos.LinkState.CLOSED);
    assertRefused(() -> publicApprovals.approve(token, session, null, null));
    assertRefused(
        () -> publicApprovals.requestChanges(token, session, "Earlier please", null, null));
    assertRefused(() -> publicApprovals.version(token, session));
    assertRefused(() -> publicApprovals.sendCode(token));
    assertThat(
            jdbc.queryForMap(
                "SELECT status, flow_stage FROM sales_ord.sales_order WHERE id = ?", orderId))
        .containsEntry("status", "DRAFT")
        .containsEntry("flow_stage", "AWAITING_CUSTOMER_APPROVAL");
  }

  @Test
  void aNewGrantToTheSamePersonDoesNotReviveTheEarlierLink() {
    String token = send();
    String session = publicApprovals.verify(token, code(token)).session();
    revokeJane();
    UUID renewed = grantJane();

    assertThat(renewed).isNotEqualTo(authorityId);
    assertThat(publicApprovals.view(token).state())
        .isEqualTo(PublicOrderApprovalDtos.LinkState.CLOSED);
    assertRefused(() -> publicApprovals.approve(token, session, null, null));
    assertThat(
            jdbc.queryForObject(
                "SELECT approval_authority_id FROM sales_ord.customer_approval"
                    + " WHERE sales_order_id = ?",
                UUID.class,
                orderId))
        .isEqualTo(authorityId);
  }

  @Test
  void anAuthorityPastItsLastDayDecidesNothing() {
    String token = send();
    String session = publicApprovals.verify(token, code(token)).session();
    jdbc.update(
        "UPDATE sales_ord.customer_approval_authority SET valid_from = ?, valid_until = ?"
            + " WHERE id = ?",
        java.sql.Date.valueOf(LocalDate.now().minusDays(30)),
        java.sql.Date.valueOf(LocalDate.now().minusDays(1)),
        authorityId);

    assertRefused(() -> publicApprovals.approve(token, session, null, null));
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM sales_ord.customer_approval WHERE sales_order_id = ?",
                String.class,
                orderId))
        .isEqualTo("SENT");
  }

  @Test
  void anAddressEditedOnTheCardReachesNobodyUntilTheAuthorityIsGrantedAgainThere() {
    String token = send();
    String session = publicApprovals.verify(token, code(token)).session();

    editJanesAddress("jane.smith@elsewhere.example");

    // Jane's authority was granted for her earlier mailbox: the edit ended it, in the same commit.
    assertThat(
            jdbc.queryForMap(
                "SELECT revoked_at IS NOT NULL AS ended, revocation_cause"
                    + " FROM sales_ord.customer_approval_authority WHERE id = ?",
                authorityId))
        .containsEntry("ended", true)
        .containsEntry("revocation_cause", "CONTACT_ADDRESS_CHANGED");
    assertThat(publicApprovals.view(token).state())
        .isEqualTo(PublicOrderApprovalDtos.LinkState.CLOSED);
    assertRefused(
        () -> publicApprovals.sendCode(token), ApproverAuthorities.APPROVER_EMAIL_CHANGED);
    assertRefused(
        () -> publicApprovals.approve(token, session, null, null),
        ApproverAuthorities.APPROVER_EMAIL_CHANGED);
    assertRefused(
        () -> asSales(() -> customerApprovals.resend(orderId, sales)),
        ApproverAuthorities.APPROVER_EMAIL_CHANGED);
    CustomerApprovalDtos.State state = asSales(() -> customerApprovals.state(orderId, sales));
    assertThat(state.approverEmail()).isNull();
    assertThat(state.actions())
        .filteredOn(action -> action.action() == CustomerApprovalDtos.Action.RESEND_LINK)
        .singleElement()
        .satisfies(
            action ->
                assertThat(action.reason()).isEqualTo(ApproverAuthorities.APPROVER_EMAIL_CHANGED));
    verify(mailer, never())
        .sendApprovalRequest(any(), eq("jane.smith@elsewhere.example"), any(), any(), any(), any());
    verify(mailer, never())
        .sendCode(any(), eq("jane.smith@elsewhere.example"), any(), any(), any());
    assertThat(
            jdbc.queryForMap(
                "SELECT status, recipient_email FROM sales_ord.customer_approval"
                    + " WHERE sales_order_id = ?",
                orderId))
        .containsEntry("status", "SENT")
        .containsEntry("recipient_email", "jane@northern-garments.co.uk");

    // A new grant needs the address confirmed as it reads now; the earlier one is refused.
    assertRefused(
        () -> grantJane("jane@northern-garments.co.uk"),
        ApproverAuthorities.APPROVER_EMAIL_CHANGED);
    // An authorised user confirms the new address is Jane's: the authority is granted again there.
    UUID renewed = grantJane("jane.smith@elsewhere.example");
    java.util.List<ApprovalAuthorityView> recorded =
        asSales(() -> authorityService.list(partnerId));
    assertThat(recorded)
        .filteredOn(view -> view.id().equals(renewed))
        .singleElement()
        .satisfies(
            view -> {
              assertThat(view.authorisedEmail()).isEqualTo("jane.smith@elsewhere.example");
              assertThat(view.contactAddressChanged()).isFalse();
              assertThat(view.active()).isTrue();
            });
    assertThat(recorded)
        .filteredOn(view -> view.id().equals(authorityId))
        .singleElement()
        .satisfies(
            view -> {
              assertThat(view.authorisedEmail()).isEqualTo("jane@northern-garments.co.uk");
              assertThat(view.contactAddressChanged()).isTrue();
              assertThat(view.revokedAt()).isNotNull();
              assertThat(view.revocationCause())
                  .isEqualTo(
                      com.fabricmanagement.sales.salesorder.domain.ApprovalAuthorityEnd
                          .CONTACT_ADDRESS_CHANGED);
            });
    // The earlier request stays bound to the earlier authority and stays closed.
    assertThat(publicApprovals.view(token).state())
        .isEqualTo(PublicOrderApprovalDtos.LinkState.CLOSED);
  }

  @Test
  void anAddressChangedAndChangedBackKeepsTheEarlierAuthorityLinkCodeAndSessionClosed() {
    String token = send();
    String session = publicApprovals.verify(token, code(token)).session();
    // Another code is on its way to Jane (queued) when her card changes.
    jdbc.update(
        "UPDATE sales_ord.customer_approval SET code_sent_at = code_sent_at - interval '1 hour'"
            + " WHERE sales_order_id = ?",
        orderId);
    publicApprovals.sendCode(token);
    ArgumentCaptor<String> codes = ArgumentCaptor.forClass(String.class);
    verify(mailer, times(2))
        .sendCode(eq(tenantId), eq("jane@northern-garments.co.uk"), any(), any(), codes.capture());
    String queuedCode = codes.getValue();

    // A → B → A
    editJanesAddress("jane.smith@elsewhere.example");
    editJanesAddress("jane@northern-garments.co.uk");

    // The card reads Jane's authorised address again; her authority ended with the first change.
    assertThat(
            jdbc.queryForMap(
                "SELECT revoked_at IS NOT NULL AS ended, revocation_cause"
                    + " FROM sales_ord.customer_approval_authority WHERE id = ?",
                authorityId))
        .containsEntry("ended", true)
        .containsEntry("revocation_cause", "CONTACT_ADDRESS_CHANGED");
    assertThat(publicApprovals.view(token).state())
        .isEqualTo(PublicOrderApprovalDtos.LinkState.CLOSED);
    // The code that was on its way opens nothing; the session already given shows nothing.
    assertRefused(
        () -> publicApprovals.verify(token, queuedCode),
        ApproverAuthorities.APPROVER_EMAIL_CHANGED);
    assertRefused(
        () -> publicApprovals.version(token, session), ApproverAuthorities.APPROVER_EMAIL_CHANGED);
    assertRefused(
        () -> publicApprovals.approve(token, session, null, null),
        ApproverAuthorities.APPROVER_EMAIL_CHANGED);
    assertRefused(
        () -> publicApprovals.requestChanges(token, session, "Earlier please", null, null),
        ApproverAuthorities.APPROVER_EMAIL_CHANGED);
    assertRefused(
        () -> publicApprovals.sendCode(token), ApproverAuthorities.APPROVER_EMAIL_CHANGED);
    assertRefused(
        () -> asSales(() -> customerApprovals.resend(orderId, sales)),
        ApproverAuthorities.APPROVER_EMAIL_CHANGED);
    assertThat(asSales(() -> customerApprovals.state(orderId, sales)).approverEmail()).isNull();

    // Only a new grant brings an authority back, and it never reopens the earlier request.
    UUID renewed = grantJane();
    assertThat(renewed).isNotEqualTo(authorityId);
    assertThat(publicApprovals.view(token).state())
        .isEqualTo(PublicOrderApprovalDtos.LinkState.CLOSED);
    assertRefused(
        () -> publicApprovals.approve(token, session, null, null),
        ApproverAuthorities.APPROVER_EMAIL_CHANGED);
    assertThat(
            jdbc.queryForMap(
                "SELECT status, approval_authority_id FROM sales_ord.customer_approval"
                    + " WHERE sales_order_id = ?",
                orderId))
        .containsEntry("status", "SENT")
        .containsEntry("approval_authority_id", authorityId);
    assertThat(
            jdbc.queryForMap(
                "SELECT status, flow_stage FROM sales_ord.sales_order WHERE id = ?", orderId))
        .containsEntry("status", "DRAFT")
        .containsEntry("flow_stage", "AWAITING_CUSTOMER_APPROVAL");
  }

  @Test
  void aRevocationInProgressMakesTheDecisionWaitAndThenRefusesIt() throws Exception {
    String token = send();
    String session = publicApprovals.verify(token, code(token)).session();
    CountDownLatch revocationHoldsTheLock = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      // The revocation locks Jane's authority and keeps its transaction open...
      CompletableFuture<Object> revocation =
          CompletableFuture.supplyAsync(
              () ->
                  attempt(
                      () ->
                          asSales(
                              () ->
                                  transactions.execute(
                                      status -> {
                                        Object view = revoke(authorityId);
                                        revocationHoldsTheLock.countDown();
                                        await(release);
                                        return view;
                                      }))),
              pool);
      assertThat(revocationHoldsTheLock.await(20, TimeUnit.SECONDS)).isTrue();
      // ...while Jane approves: her decision waits for the authority instead of reading the state
      // before the revocation.
      CompletableFuture<Object> decision =
          CompletableFuture.supplyAsync(
              () -> attempt(() -> publicApprovals.approve(token, session, null, null)), pool);
      awaitAWaitingTransaction();
      assertThat(decision).isNotDone();

      release.countDown();

      assertThat(revocation.get(20, TimeUnit.SECONDS)).isInstanceOf(ApprovalAuthorityView.class);
      assertThat(decision.get(20, TimeUnit.SECONDS))
          .isInstanceOfSatisfying(
              OrderDomainException.class,
              exception ->
                  assertThat(exception.getErrorCode())
                      .isEqualTo(ApproverAuthorities.AUTHORITY_INACTIVE));
    } finally {
      release.countDown();
      pool.shutdownNow();
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM sales_ord.customer_approval WHERE sales_order_id = ?",
                String.class,
                orderId))
        .isEqualTo("SENT");
    assertThat(
            jdbc.queryForMap(
                "SELECT status, flow_stage FROM sales_ord.sales_order WHERE id = ?", orderId))
        .containsEntry("status", "DRAFT")
        .containsEntry("flow_stage", "AWAITING_CUSTOMER_APPROVAL");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM sales_ord.delivery_commitment WHERE sales_order_id = ?",
                Integer.class,
                orderId))
        .isZero();
  }

  @Test
  void aDecisionInProgressFinishesBeforeTheRevocation() throws Exception {
    String token = send();
    String session = publicApprovals.verify(token, code(token)).session();
    UUID approval = approvalId();
    String presented = OrderVersionSnapshotter.sha256(session);
    CustomerApproval.Decider jane =
        new CustomerApproval.Decider(
            CustomerApprovalChannel.EMAIL_LINK,
            "Jane Smith",
            "jane@northern-garments.co.uk",
            null,
            null,
            null);
    CountDownLatch decisionHoldsTheLock = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      // Jane's approval checks her authority under its lock and keeps its transaction open...
      CompletableFuture<Object> decision =
          CompletableFuture.supplyAsync(
              () ->
                  attempt(
                      () ->
                          asCustomer(
                              () ->
                                  transactions.execute(
                                      status -> {
                                        Object outcome =
                                            decisions.approve(
                                                approval,
                                                jane,
                                                (value, now) -> {
                                                  if (!value.hasSession(presented, now)) {
                                                    throw new IllegalStateException("No session");
                                                  }
                                                });
                                        decisionHoldsTheLock.countDown();
                                        await(release);
                                        return outcome;
                                      }))),
              pool);
      assertThat(decisionHoldsTheLock.await(20, TimeUnit.SECONDS)).isTrue();
      // ...so the revocation waits until her decision is recorded.
      CompletableFuture<Object> revocation =
          CompletableFuture.supplyAsync(() -> attempt(() -> revoke(authorityId)), pool);
      awaitAWaitingTransaction();
      assertThat(revocation).isNotDone();

      release.countDown();

      assertThat(decision.get(20, TimeUnit.SECONDS))
          .isInstanceOfSatisfying(
              CustomerApprovalDecisionService.Outcome.class,
              outcome -> assertThat(outcome.status()).isEqualTo(CustomerApprovalStatus.APPROVED));
      assertThat(revocation.get(20, TimeUnit.SECONDS))
          .isInstanceOfSatisfying(
              ApprovalAuthorityView.class, view -> assertThat(view.revokedAt()).isNotNull());
    } finally {
      release.countDown();
      pool.shutdownNow();
    }
    // The decision came first and stands; the revocation applies from then on.
    assertThat(
            jdbc.queryForMap(
                "SELECT status, flow_stage FROM sales_ord.sales_order WHERE id = ?", orderId))
        .containsEntry("status", "CONFIRMED")
        .containsEntry("flow_stage", "CUSTOMER_APPROVED");
    assertThat(
            jdbc.queryForObject(
                "SELECT revoked_at IS NOT NULL FROM sales_ord.customer_approval_authority"
                    + " WHERE id = ?",
                Boolean.class,
                authorityId))
        .isTrue();
  }

  /** An authorised user grants Jane the authority at the address her contact point reads now. */
  private UUID grantJane() {
    return grantJane("jane@northern-garments.co.uk");
  }

  /** An authorised user grants Jane the authority, confirming {@code address} as hers. */
  private UUID grantJane(String address) {
    return asSales(
        () ->
            authorityService
                .grant(
                    partnerId,
                    new GrantApprovalAuthorityRequest(
                        "Jane Smith",
                        janesAddress,
                        address,
                        ApprovalAuthorityBasis.WRITTEN_MANDATE,
                        "Mandate letter from the managing director",
                        LocalDate.now().minusDays(1),
                        null),
                    sales)
                .id());
  }

  private void revokeJane() {
    revoke(authorityId);
  }

  private ApprovalAuthorityView revoke(UUID authority) {
    return asSales(
        () ->
            authorityService.revoke(
                partnerId,
                authority,
                new RevokeApprovalAuthorityRequest("Jane left the company"),
                sales));
  }

  /** Someone edits Jane's contact point on the customer's card. */
  private void editJanesAddress(String address) {
    asSales(
        () ->
            organizationContacts.editOrganizationContact(
                customerOrganizationId,
                janesAddress,
                EditOrganizationContactRequest.builder()
                    .contactValue(address)
                    .contactType(ContactType.EMAIL)
                    .build()));
  }

  /** Runs {@code step} in the tenant as the seller. */
  private <T> T asSales(java.util.function.Supplier<T> step) {
    boolean outside = TenantContext.getCurrentTenantIdOrNull() == null;
    Authentication previousAuthentication = SecurityContextHolder.getContext().getAuthentication();
    if (outside) {
      TenantContext.setCurrentTenantId(tenantId);
      TenantContext.setCurrentUserId(sales);
    }
    AuthenticatedUserContext principal =
        new AuthenticatedUserContext(sales, "WORKER", java.util.List.of(), null, tenantId);
    UsernamePasswordAuthenticationToken authentication =
        UsernamePasswordAuthenticationToken.authenticated(principal, "n/a", java.util.List.of());
    authentication.setDetails(principal);
    SecurityContextHolder.getContext().setAuthentication(authentication);
    try {
      return step.get();
    } finally {
      SecurityContextHolder.getContext().setAuthentication(previousAuthentication);
      if (outside) {
        TenantContext.clear();
      }
    }
  }

  /** Runs {@code step} in the tenant as the system, the way the customer's link does. */
  private Object asCustomer(java.util.function.Supplier<Object> step) {
    TenantContext.setCurrentTenantId(tenantId);
    TenantContext.setCurrentUserId(SystemUser.ID);
    try {
      return step.get();
    } finally {
      TenantContext.clear();
    }
  }

  /** A failure is returned rather than thrown, so another thread can inspect it. */
  private static Object attempt(java.util.function.Supplier<Object> step) {
    try {
      return step.get();
    } catch (RuntimeException failure) {
      return failure;
    }
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(20, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Timed out waiting for the other transaction");
      }
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    }
  }

  /** Waits until some transaction waits for a row lock another transaction holds. */
  private void awaitAWaitingTransaction() throws InterruptedException {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
    while (System.nanoTime() < deadline) {
      Integer waiting =
          jdbc.queryForObject(
              "SELECT count(*) FROM pg_locks WHERE locktype = 'transactionid' AND NOT granted",
              Integer.class);
      if (waiting != null && waiting > 0) {
        return;
      }
      Thread.sleep(50);
    }
    fail("No transaction waited for the approval authority's lock");
  }

  private UUID approvalId() {
    return jdbc.queryForObject(
        "SELECT id FROM sales_ord.customer_approval WHERE sales_order_id = ?", UUID.class, orderId);
  }

  private static void assertRefused(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
    assertRefused(call, ApproverAuthorities.AUTHORITY_INACTIVE);
  }

  private static void assertRefused(
      org.assertj.core.api.ThrowableAssert.ThrowingCallable call, String code) {
    assertThatThrownBy(call)
        .isInstanceOfSatisfying(
            OrderDomainException.class,
            exception -> assertThat(exception.getErrorCode()).isEqualTo(code));
  }

  /** Sales sends the planned order; returns the link the customer received. */
  private String send() {
    TenantContext.setCurrentTenantId(tenantId);
    TenantContext.setCurrentUserId(sales);
    try {
      CustomerApprovalDtos.State state = customerApprovals.sendForApproval(orderId, null, sales);
      assertThat(state.approvals())
          .singleElement()
          .satisfies(
              view -> {
                assertThat(view.recipientEmail()).isEqualTo("jane@northern-garments.co.uk");
                assertThat(view.linkExpiresAt()).isNotNull();
              });
    } finally {
      TenantContext.clear();
    }
    ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
    verify(mailer)
        .sendApprovalRequest(
            eq(tenantId), eq("jane@northern-garments.co.uk"), any(), any(), token.capture(), any());
    return token.getValue();
  }

  /** The customer asks for the one-time code; returns the code e-mailed to them. */
  private String code(String token) {
    publicApprovals.sendCode(token);
    ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
    verify(mailer)
        .sendCode(eq(tenantId), eq("jane@northern-garments.co.uk"), any(), any(), code.capture());
    return code.getValue();
  }
}
