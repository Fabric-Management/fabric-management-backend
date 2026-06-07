package com.fabricmanagement.sales;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import com.fabricmanagement.production.execution.workorder.app.WorkOrderService;
import com.fabricmanagement.production.execution.workorder.domain.FulfillmentType;
import com.fabricmanagement.production.execution.workorder.domain.WorkOrderStatus;
import com.fabricmanagement.production.execution.workorder.domain.event.WorkOrderApprovedEvent;
import com.fabricmanagement.production.execution.workorder.dto.WorkOrderRequest;
import com.fabricmanagement.production.execution.workorder.dto.WorkOrderResponse;
import com.fabricmanagement.sales.salesorder.app.SalesOrderService;
import com.fabricmanagement.sales.salesorder.domain.event.SalesOrderConfirmedEvent;
import com.fabricmanagement.sales.salesorder.dto.CreateSalesOrderRequest;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.annotation.Commit;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@DisabledIf(value = "dockerNotAvailable", disabledReason = "Docker is not available")
@DisplayName("Domain events publication (SalesOrder / WorkOrder)")
class DomainEventsPublicationIT {

  static boolean dockerNotAvailable() {
    return !org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
  }

  @Container
  @SuppressWarnings("resource")
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:15-alpine"))
          .withDatabaseName("fabric_test")
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

  @Autowired private SalesOrderService salesOrderService;
  @Autowired private WorkOrderService workOrderService;
  @Autowired private TradingPartnerService tradingPartnerService;
  @Autowired private TenantRepository tenantRepository;
  @Autowired private OrganizationRepository organizationRepository;

  @SpyBean private DomainEventPublisher domainEventPublisher;
  @SpyBean private EventRouterService eventRouterService;

  private UUID tenantId;

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
    organizationRepository.save(org);
    TenantContext.clear();

    clearInvocations(domainEventPublisher, eventRouterService);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  @Commit
  @DisplayName("confirmOrder publishes SalesOrderConfirmedEvent and routes after commit")
  void confirmOrder_publishesSalesOrderConfirmedEvent() {
    TenantContext.setCurrentTenantId(tenantId);

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
    SalesOrderDto confirmed = salesOrderService.confirmOrder(created.getId());

    assertThat(confirmed.getStatus().name()).isEqualTo("CONFIRMED");

    verify(domainEventPublisher).publish(any(SalesOrderConfirmedEvent.class));
    verify(eventRouterService, timeout(15_000)).route(any(SalesOrderConfirmedEvent.class));
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

    verify(domainEventPublisher).publish(any(WorkOrderApprovedEvent.class));
    verify(eventRouterService, timeout(15_000)).route(any(WorkOrderApprovedEvent.class));
  }
}
