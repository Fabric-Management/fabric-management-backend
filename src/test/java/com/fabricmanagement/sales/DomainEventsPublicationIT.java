package com.fabricmanagement.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.flowboard.generator.app.EventRouterService;
import com.fabricmanagement.platform.organization.domain.Organization;
import com.fabricmanagement.platform.organization.domain.OrganizationType;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationRepository;
import com.fabricmanagement.platform.tenant.domain.Tenant;
import com.fabricmanagement.platform.tenant.infra.repository.TenantRepository;
import com.fabricmanagement.platform.tradingpartner.app.TradingPartnerService;
import com.fabricmanagement.platform.tradingpartner.domain.PartnerType;
import com.fabricmanagement.platform.tradingpartner.dto.CreateTradingPartnerRequest;
import com.fabricmanagement.platform.tradingpartner.dto.TradingPartnerDto;
import com.fabricmanagement.platform.user.domain.Role;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.infra.repository.RoleRepository;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import com.fabricmanagement.production.core.workorder.app.WorkOrderService;
import com.fabricmanagement.production.core.workorder.domain.FulfillmentType;
import com.fabricmanagement.production.core.workorder.domain.WorkOrderStatus;
import com.fabricmanagement.production.core.workorder.domain.event.WorkOrderApprovedEvent;
import com.fabricmanagement.production.core.workorder.dto.WorkOrderRequest;
import com.fabricmanagement.production.core.workorder.dto.WorkOrderResponse;
import com.fabricmanagement.sales.salesorder.app.SalesOrderService;
import com.fabricmanagement.sales.salesorder.domain.event.SalesOrderConfirmedEvent;
import com.fabricmanagement.sales.salesorder.dto.CreateSalesOrderRequest;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderDto;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Commit;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@DisplayName("Domain events publication (SalesOrder / WorkOrder)")
class DomainEventsPublicationIT extends AbstractIntegrationTest {

  @Autowired private SalesOrderService salesOrderService;
  @Autowired private WorkOrderService workOrderService;
  @Autowired private TradingPartnerService tradingPartnerService;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private OrganizationRepository organizationRepository;
  @Autowired private RoleRepository roleRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private JdbcTemplate jdbc;

  @MockitoSpyBean private DomainEventPublisher domainEventPublisher;
  @MockitoSpyBean private EventRouterService eventRouterService;

  private UUID tenantId;
  private UUID userId;

  @BeforeEach
  void setUpTenant() {
    long timestamp = System.currentTimeMillis();
    String uid = "EVT-" + timestamp % 100000;

    Tenant tenant = Tenant.create("DomainEvents IT " + timestamp, uid);
    tenant.activate("test");
    tenant = tenantRepository.save(tenant);
    tenantId = tenant.getId();

    TenantContext.setCurrentTenantId(tenantId);
    Organization org =
        Organization.create(
            "Org " + timestamp, "TAX" + timestamp % 100000, OrganizationType.SPINNER);
    org = organizationRepository.save(org);
    Role role = roleRepository.save(Role.create("Events admin", "ADMIN", "Event test actor"));
    User actor = User.create("Events", "Admin", org.getId());
    actor.setRole(role);
    userId = userRepository.save(actor).getId();
    TenantContext.clear();

    clearInvocations(domainEventPublisher, eventRouterService);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
    // The database is shared and Modulith replays unfinished publications on every context start.
    // The partner created below has no sales ownership policy, so its
    // CustomerRelationshipEstablishedEvent can never complete. Do not leave it behind.
    jdbc.update(
        "delete from event_publication where completion_date is null and serialized_event like ?",
        "%" + tenantId + "%");
  }

  @Test
  @Commit
  @DisplayName("Confirmation publishes SalesOrderConfirmedEvent and routes after commit")
  void confirmation_publishesSalesOrderConfirmedEvent() {
    TenantContext.setCurrentTenantId(tenantId);
    TenantContext.setCurrentUserId(userId);

    TradingPartnerDto partner =
        tradingPartnerService.createPartner(
            CreateTradingPartnerRequest.builder()
                .taxId("TAXSO" + System.currentTimeMillis() % 100000)
                .companyName("Customer Co")
                .partnerType(PartnerType.CUSTOMER)
                .country("TUR")
                .build());

    LocalDate delivery = LocalDate.now().plusDays(10);
    CreateSalesOrderRequest req = new CreateSalesOrderRequest();
    req.setPartnerId(partner.getId());
    req.setOrderDate(LocalDate.now());
    req.setRequestedDeliveryDate(delivery);

    SalesOrderDto created = salesOrderService.createOrder(req);
    SalesOrderDto confirmed = salesOrderService.confirmDemoSeedOrder(created.getId());

    assertThat(confirmed.getStatus().name()).isEqualTo("CONFIRMED");

    UUID orderId = created.getId();
    verify(domainEventPublisher).publish(argThat(event -> isConfirmationOf(event, orderId)));
    verify(eventRouterService, timeout(15_000))
        .route(argThat(event -> isConfirmationOf(event, orderId)));
  }

  @Test
  @Commit
  @DisplayName("changeStatus(APPROVED) publishes WorkOrderApprovedEvent and routes after commit")
  void changeStatusApproved_publishesWorkOrderApprovedEvent() {
    UUID approver = UUID.randomUUID();
    TenantContext.setCurrentTenantId(tenantId);
    TenantContext.setCurrentUserId(approver);

    WorkOrderResponse draft =
        workOrderService.createWorkOrder(
            WorkOrderRequest.builder()
                .fulfillmentType(FulfillmentType.INTERNAL)
                .plannedQty(BigDecimal.ONE)
                .unit("KG")
                .build());

    workOrderService.changeStatus(draft.id(), WorkOrderStatus.PENDING_APPROVAL);
    workOrderService.changeStatus(draft.id(), WorkOrderStatus.APPROVED);

    UUID workOrderId = draft.id();
    verify(domainEventPublisher).publish(argThat(event -> isApprovalOf(event, workOrderId)));
    verify(eventRouterService, timeout(15_000))
        .route(argThat(event -> isApprovalOf(event, workOrderId)));
  }

  /** Match this test's own aggregate: the shared database may replay other classes' events. */
  private static boolean isConfirmationOf(Object event, UUID salesOrderId) {
    return event instanceof SalesOrderConfirmedEvent confirmed
        && salesOrderId.equals(confirmed.getSalesOrderId());
  }

  private static boolean isApprovalOf(Object event, UUID workOrderId) {
    return event instanceof WorkOrderApprovedEvent approved
        && workOrderId.equals(approved.getWorkOrderId());
  }
}
