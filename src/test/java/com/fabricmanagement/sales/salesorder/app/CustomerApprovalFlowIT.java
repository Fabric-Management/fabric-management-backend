package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.PermissionEvaluator;
import com.fabricmanagement.common.infrastructure.security.dto.PermissionResult;
import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.platform.organization.domain.Organization;
import com.fabricmanagement.platform.organization.domain.OrganizationType;
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
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.infra.repository.RoleRepository;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import com.fabricmanagement.sales.common.exception.OrderDomainException;
import com.fabricmanagement.sales.salesorder.domain.DeliveryProposal;
import com.fabricmanagement.sales.salesorder.domain.DeliveryTerm;
import com.fabricmanagement.sales.salesorder.domain.DeliveryTerms;
import com.fabricmanagement.sales.salesorder.domain.IncotermsVersion;
import com.fabricmanagement.sales.salesorder.domain.OrderStatus;
import com.fabricmanagement.sales.salesorder.domain.OrderVersionContent;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import com.fabricmanagement.sales.salesorder.dto.CustomerApprovalDtos;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
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

  @MockitoBean private PermissionEvaluator permissionEvaluator;
  @MockitoBean private CustomerApprovalMailer mailer;

  private UUID tenantId;
  private UUID sales;
  private UUID orderId;

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
    UUID partnerId =
        partners
            .saveAndFlush(
                TradingPartner.create(
                    registries.save(registry), PartnerType.CUSTOMER, "Northern Garments"))
            .getId();
    SalesOrder order =
        SalesOrder.builder()
            .tradingPartnerId(partnerId)
            .orderNumber("SO-" + suffix)
            .status(OrderStatus.DRAFT)
            .orderDate(LocalDate.now())
            .contactName("Jane Smith")
            .contactEmail("jane@northern-garments.co.uk")
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
