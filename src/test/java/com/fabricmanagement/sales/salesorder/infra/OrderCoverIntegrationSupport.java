package com.fabricmanagement.sales.salesorder.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fabricmanagement.common.infrastructure.approval.ApprovalPort;
import com.fabricmanagement.common.infrastructure.events.DomainEventPublisher;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.AuthenticatedUserContext;
import com.fabricmanagement.common.infrastructure.security.PermissionEvaluator;
import com.fabricmanagement.common.util.Money;
import com.fabricmanagement.common.util.OrderTotals;
import com.fabricmanagement.flowboard.routing.app.RoutingPoolConfigurationService;
import com.fabricmanagement.flowboard.routing.app.listener.RoutingEventListener;
import com.fabricmanagement.flowboard.routing.domain.RoutingPoolKey;
import com.fabricmanagement.flowboard.routing.domain.event.RoutingPoolChangedEvent;
import com.fabricmanagement.flowboard.routing.infra.repository.RoutingRepository;
import com.fabricmanagement.flowboard.task.api.DecisionTaskTransitionController;
import com.fabricmanagement.flowboard.task.dto.DecisionTransitionRequest;
import com.fabricmanagement.flowboard.task.dto.DecisionTransitionResult;
import com.fabricmanagement.inventory.location.domain.WarehouseLocation;
import com.fabricmanagement.inventory.location.domain.WarehouseLocationType;
import com.fabricmanagement.inventory.location.infra.repository.WarehouseLocationRepository;
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
import com.fabricmanagement.platform.user.domain.PermissionTemplate;
import com.fabricmanagement.platform.user.domain.Role;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.infra.repository.PermissionTemplateRepository;
import com.fabricmanagement.platform.user.infra.repository.RoleRepository;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import com.fabricmanagement.product.core.domain.Product;
import com.fabricmanagement.product.core.domain.ProductType;
import com.fabricmanagement.product.core.infra.repository.ProductRepository;
import com.fabricmanagement.production.core.workorder.app.WorkOrderService;
import com.fabricmanagement.sales.salesorder.app.OrderCoverActivationService;
import com.fabricmanagement.sales.salesorder.app.OrderCoverEvidenceService;
import com.fabricmanagement.sales.salesorder.app.SalesOrderService;
import com.fabricmanagement.sales.salesorder.domain.*;
import com.fabricmanagement.sales.salesorder.domain.port.OrderCoverEvidencePort;
import com.fabricmanagement.sales.salesorder.domain.requirement.*;
import com.fabricmanagement.sales.salesorder.dto.ConfirmProductionCoverPayload;
import com.fabricmanagement.sales.salesorder.dto.OrderCoverEvidenceDto;
import com.fabricmanagement.sales.salesorder.infra.repository.*;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Committed fixtures and real Spring transaction boundaries. Permission evaluation, production
 * creation and receipt persistence are never replaced by mocks. Focused evidence scenarios spy the
 * authoritative port with complete inputs. The routing event listener is paused deliberately so
 * pool edits can be tested before asynchronous repair.
 */
@AutoConfigureMockMvc(addFilters = false)
@ResourceLock("sales-order-creation-sequence")
public abstract class OrderCoverIntegrationSupport extends AbstractIntegrationTest {
  // JWT parsing is outside this slice; the same authenticated context is bound explicitly.
  // Controller checks, method security and the actual exception advice remain enabled.
  @Autowired protected MockMvc mvc;
  @Autowired protected PlatformTransactionManager transactions;
  @Autowired protected TenantRepository tenants;
  @Autowired protected OrganizationRepository organizations;
  @Autowired protected RoleRepository roles;
  @Autowired protected UserRepository users;
  @Autowired protected PermissionTemplateRepository permissions;
  @Autowired protected PermissionEvaluator permissionEvaluator;
  @Autowired protected TradingPartnerRegistryRepository registries;
  @Autowired protected TradingPartnerRepository partners;
  @Autowired protected SalesOrderRepository orders;
  @Autowired protected SalesOrderLineRepository lines;
  @Autowired protected RequirementProfileVersionRepository profiles;
  @Autowired protected SalesOrderService sales;
  @Autowired protected OrderCoverActivationService activation;
  @Autowired protected OrderCoverEvidenceService evidence;
  @Autowired protected DecisionTaskTransitionController controller;
  @Autowired protected RoutingPoolConfigurationService routingConfiguration;
  @Autowired protected RoutingRepository routing;
  @Autowired protected ProductRepository products;
  @Autowired protected WarehouseLocationRepository locations;
  @Autowired protected ObjectMapper mapper;

  @MockitoSpyBean protected RoutingEventListener routingListener;
  @MockitoSpyBean protected DomainEventPublisher events;
  @MockitoSpyBean protected ApprovalPort approval;
  @MockitoSpyBean protected WorkOrderService production;
  @MockitoSpyBean protected OrderCoverEvidencePort evidenceSource;
  protected JdbcTemplate jdbc;
  protected UUID tenant;
  protected UUID board;
  protected UUID partner;
  protected Organization organization;
  protected User actor;
  protected String suffix;

  @Autowired
  void orderCoverJdbc(@Qualifier("dataSource") DataSource dataSource) {
    jdbc = new JdbcTemplate(dataSource);
  }

  @BeforeEach
  void orderCoverFixture() {
    RoutingEventListener target = AopTestUtils.getUltimateTargetObject(routingListener);
    doThrow(new IllegalStateException("Routing repair is paused for the integration scenario"))
        .when(target)
        .onRoutingPoolChanged(any(RoutingPoolChangedEvent.class));
    suffix = UUID.randomUUID().toString().substring(0, 8);
    tenant = tenants.saveAndFlush(Tenant.create("Cover " + suffix, "OC-" + suffix)).getId();
    TenantContext.setCurrentTenantId(tenant);
    TenantContext.setCurrentTenantUid("OC-" + suffix);
    organization =
        organizations.saveAndFlush(
            Organization.create("Cover org " + suffix, "OC-" + suffix, OrganizationType.SPINNER));
    actor = user("Operator", "sales:read", "sales:write", "flowboard:read", "flowboard:write");
    authenticate(actor);
    board = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO flowboard.board
          (id,tenant_id,uid,name,board_type,wip_limit_default,default_view_type,
           is_active,created_at,updated_at,version)
        VALUES (?,?,?,'Order cover integration','GLOBAL',5,'KANBAN',true,now(),now(),0)
        """,
        board,
        tenant,
        board.toString());
    var registry = TradingPartnerRegistry.create(null, "Cover customer " + suffix, "GBR");
    registry.setUid("OCR-" + UUID.randomUUID());
    registry = registries.saveAndFlush(registry);
    partner =
        partners
            .saveAndFlush(TradingPartner.create(registry, PartnerType.CUSTOMER, "Cover customer"))
            .getId();
    // Initial pool setup has no tasks to repair. Publishing its deliberately-failed repair event
    // once per test can outrun the async executor and exhaust the ten-connection test pool.
    doNothing().when(events).publish(isA(RoutingPoolChangedEvent.class));
    try {
      configurePool(actor.getId());
    } finally {
      doCallRealMethod().when(events).publish(isA(RoutingPoolChangedEvent.class));
    }
    template("SalesOrderConfirmed", com.fabricmanagement.flowboard.task.domain.TaskType.PRODUCTION);
    template("SalesOrderConfirmed", com.fabricmanagement.flowboard.task.domain.TaskType.WAREHOUSE);
    template("WorkOrderApproved", com.fabricmanagement.flowboard.task.domain.TaskType.PRODUCTION);
    template(
        "WorkOrderRecipeAssignmentNeeded",
        com.fabricmanagement.flowboard.task.domain.TaskType.RECIPE_ASSIGNMENT);
  }

  private void template(String event, com.fabricmanagement.flowboard.task.domain.TaskType type) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        """
        INSERT INTO flowboard.task_template
          (id,tenant_id,uid,name,event_type,title_template,task_type,module_type,
           default_priority,default_assignee_role,is_active,created_at,updated_at,version)
        VALUES (?,?,?,?,?,?,?,'GENERAL','HIGH','ANY',true,now(),now(),0)
        """,
        id,
        tenant,
        id.toString(),
        "Cover integration " + event + " " + type,
        event,
        "Review {entityRef}",
        type.name());
  }

  @AfterEach
  void clearOrderCoverContext() {
    SecurityContextHolder.clearContext();
    TenantContext.clear();
  }

  protected User user(String name, String... grants) {
    Role role =
        roles.saveAndFlush(Role.create(name + suffix, "OC-" + UUID.randomUUID(), "Cover test"));
    User user = User.create(name, suffix, organization.getId());
    user.setRole(role);
    user = users.saveAndFlush(user);
    for (String grant : grants) {
      String[] key = grant.split(":");
      permissions.saveAndFlush(
          PermissionTemplate.builder()
              .roleCode(role.getRoleCode())
              .resource(key[0])
              .action(key[1])
              .dataScope(DataScope.GLOBAL)
              .build());
    }
    return user;
  }

  protected void authenticate(User user) {
    TenantContext.setCurrentTenantId(tenant);
    TenantContext.setCurrentTenantUid("OC-" + suffix);
    TenantContext.setCurrentUserId(user.getId());
    var context =
        new AuthenticatedUserContext(
            user.getId(), user.getRole().getRoleCode(), List.of(), null, tenant);
    var authentication = UsernamePasswordAuthenticationToken.authenticated(context, "", List.of());
    authentication.setDetails(context);
    SecurityContextHolder.getContext().setAuthentication(authentication);
  }

  protected void configurePool(UUID... members) {
    Long revision =
        routing.pool(tenant, RoutingPoolKey.ORDER_COVER).map(p -> p.revision()).orElse(null);
    routingConfiguration.configure(tenant, RoutingPoolKey.ORDER_COVER, revision, Set.of(members));
  }

  protected UUID draft(int lineCount) {
    return tx(
        () -> {
          var order =
              orders.saveAndFlush(
                  SalesOrder.builder()
                      .tradingPartnerId(partner)
                      .orderNumber("OC-" + UUID.randomUUID())
                      .orderDate(LocalDate.now())
                      .status(OrderStatus.DRAFT)
                      .totals(OrderTotals.zero("GBP"))
                      .build());
          for (int index = 0; index < lineCount; index++) {
            var line =
                lines.saveAndFlush(
                    SalesOrderLine.builder()
                        .salesOrderId(order.getId())
                        .productDesc("Customer specified textile " + index)
                        .requestedQty(new BigDecimal("10.000"))
                        .unit("kg")
                        .unitPrice(Money.of(BigDecimal.ONE, "GBP"))
                        .moduleType(com.fabricmanagement.sales.salesorder.domain.ModuleType.FABRIC)
                        .build());
            attachProfile(line, UUID.randomUUID(), 1, true);
          }
          return order.getId();
        });
  }

  protected void attachProfile(SalesOrderLine line, UUID profileId, int version, boolean complete) {
    var basis =
        new RequirementProfileBasis(
            RequirementProfileBasis.Kind.LINE_EXPLICIT,
            null,
            null,
            null,
            null,
            actor.getId(),
            Instant.parse("2026-09-19T12:00:00Z"),
            "Customer instruction OC-" + version);
    var composition =
        new UnmodelledSpecConstraint(
            "composition",
            UnmodelledSpecConstraint.Status.RESOLVED_UNSUPPORTED,
            mapper.createObjectNode().put("cottonPercent", version == 1 ? 100 : 80),
            "percent",
            "Required cotton composition",
            "No stock comparator for this constraint");
    var input =
        new RequirementProfileInput(
            basis,
            "V1",
            "V1",
            complete ? Set.of() : Set.of("WIDTH"),
            List.of(),
            List.of(composition),
            List.of());
    var snapshot = RequirementProfileSnapshot.resolve(profileId, version, input, null, false);
    profiles.saveAndFlush(
        RequirementProfileVersion.builder()
            .profileId(profileId)
            .profileVersion(version)
            .salesOrderLineId(line.getId())
            .fingerprint(snapshot.fingerprint())
            .snapshot(snapshot)
            .build());
    line.attachRequirementProfile(snapshot);
    lines.saveAndFlush(line);
  }

  /**
   * Waits until every event publication that mentions the case has been delivered, so a test that
   * mutates the decision projection directly is not overwritten by a late async listener.
   */
  protected void awaitCaseEventsSettled(UUID caseId) {
    await()
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(
            () ->
                assertThat(
                        jdbc.queryForObject(
                            "select count(*) from event_publication where serialized_event like ?"
                                + " and completion_date is null",
                            Integer.class,
                            "%" + caseId + "%"))
                    .isZero());
  }

  protected Cover governed(int lineCount) {
    activation.activate();
    UUID orderId = draft(lineCount);
    sales.confirmOrder(orderId, actor.getId());
    return awaitCover(orderId);
  }

  protected Cover awaitCover(UUID orderId) {
    await()
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(
            () -> {
              List<UUID> attached =
                  jdbc.queryForList(
                      "select task_id from sales_ord.order_cover_case where tenant_id=? and"
                          + " sales_order_id=? and task_id is not null",
                      UUID.class,
                      tenant,
                      orderId);
              assertThat(attached).hasSize(1);
              assertThat(
                      jdbc.queryForList(
                          "select user_id from flowboard.task_assignee where task_id=? and"
                              + " is_active",
                          UUID.class,
                          attached.getFirst()))
                  .contains(actor.getId());
            });
    UUID caseId =
        jdbc.queryForObject(
            "select id from sales_ord.order_cover_case where tenant_id=? and sales_order_id=?",
            UUID.class,
            tenant,
            orderId);
    UUID taskId =
        jdbc.queryForObject(
            "select task_id from sales_ord.order_cover_case where id=?", UUID.class, caseId);
    List<UUID> lineIds =
        jdbc.queryForList(
            "select id from sales_ord.sales_order_line where sales_order_id=? order by"
                + " created_at,id",
            UUID.class,
            orderId);
    return new Cover(orderId, caseId, taskId, lineIds);
  }

  protected OrderCoverEvidenceDto refresh(Cover cover) {
    return evidence.refresh(cover.orderId(), cover.caseId());
  }

  protected DecisionTransitionRequest request(
      Cover cover,
      OrderCoverEvidenceDto snapshot,
      List<UUID> selected,
      UUID key,
      String rationale) {
    return new DecisionTransitionRequest(
        DecisionTransitionRequest.Action.CONFIRM_PRODUCTION_COVER,
        taskVersion(cover),
        key,
        new ConfirmProductionCoverPayload(
            cover.caseId(), snapshot.id(), snapshot.revision(), selected, rationale, null));
  }

  protected DecisionTransitionResult settle(Cover cover, DecisionTransitionRequest command) {
    return controller.transitionDecisionTask(cover.taskId(), command).getBody().getData();
  }

  protected ResultActions postTransition(Cover cover, DecisionTransitionRequest command)
      throws Exception {
    return performAuthenticated(
        post("/api/v1/flowboard/tasks/{taskId}/transitions", cover.taskId())
            .contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsBytes(command)));
  }

  /** Supplies the request principal and restores the context cleared by MVC interceptors. */
  protected ResultActions performAuthenticated(MockHttpServletRequestBuilder request)
      throws Exception {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    UUID tenantId = TenantContext.getCurrentTenantIdOrNull();
    String tenantUid = TenantContext.getCurrentTenantUid();
    UUID userId = TenantContext.getCurrentUserId();
    String tenantCountry = TenantContext.getCurrentTenantCountry();
    try {
      return mvc.perform(request.principal(authentication));
    } finally {
      SecurityContextHolder.clearContext();
      TenantContext.clear();
      if (tenantId != null) TenantContext.setCurrentTenantId(tenantId);
      if (tenantUid != null) TenantContext.setCurrentTenantUid(tenantUid);
      if (userId != null) TenantContext.setCurrentUserId(userId);
      if (tenantCountry != null) TenantContext.setCurrentTenantCountry(tenantCountry);
      if (authentication != null) {
        SecurityContextHolder.getContext().setAuthentication(authentication);
      }
    }
  }

  protected ReservationStock reservationStock() {
    return tx(
        () -> {
          var product = products.saveAndFlush(Product.create(ProductType.FABRIC, "kg"));
          var location =
              locations.saveAndFlush(
                  new WarehouseLocation(
                      null,
                      "OC-STOCK-" + suffix,
                      "Order cover stock " + suffix,
                      null,
                      WarehouseLocationType.WAREHOUSE,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      false));
          return new ReservationStock(location.getId(), product.getId());
        });
  }

  protected OrderCoverEvidencePort.Inputs knownEvidence(
      Cover cover, String requested, String suitableFree) {
    UUID productId =
        tx(() -> products.saveAndFlush(Product.create(ProductType.FABRIC, "kg")).getId());
    tx(
        () -> {
          var line = lines.findById(cover.lineIds().getFirst()).orElseThrow();
          line.setProductId(productId);
          line.setRequestedQty(new BigDecimal(requested));
          lines.saveAndFlush(line);
          return null;
        });
    var inputs =
        new OrderCoverEvidencePort.Inputs(
            List.of(
                new OrderCoverEvidencePort.Demand(
                    cover.lineIds().getFirst(), new BigDecimal(requested), "kg", null)),
            List.of(
                new OrderCoverEvidencePort.Lot(
                    UUID.randomUUID(),
                    productId,
                    "kg",
                    new BigDecimal(suitableFree),
                    new BigDecimal(suitableFree),
                    OrderCoverEvidencePort.Eligibility.ELIGIBLE,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    "known-evidence-" + suffix)));
    stubEvidenceInputs(inputs);
    return inputs;
  }

  /**
   * Stubs the evidence port on the Mockito spy itself. The injected bean is the Spring proxy around
   * the spy, and {@code lockAndInspect} is {@code @Transactional(MANDATORY)}: stubbing through the
   * proxy runs the transaction advice before Mockito sees the call and fails outside a transaction,
   * leaving an unfinished stubbing that breaks the following tests.
   */
  protected void stubEvidenceInputs(OrderCoverEvidencePort.Inputs inputs) {
    OrderCoverEvidencePort target = AopTestUtils.getUltimateTargetObject(evidenceSource);
    doReturn(inputs).when(target).inspect(any(OrderCoverEvidencePort.Requirements.class));
    doReturn(inputs).when(target).lockAndInspect(any(OrderCoverEvidencePort.Requirements.class));
  }

  /**
   * Deactivates one grant of the user's role after setup, e.g. after pool membership was accepted.
   */
  protected void revoke(User user, String grant) {
    String[] key = grant.split(":");
    jdbc.update(
        "update common_user.permission_template set is_active=false where tenant_id=? and"
            + " role_code=? and resource=? and action=?",
        tenant,
        user.getRole().getRoleCode(),
        key[0],
        key[1]);
  }

  protected long taskVersion(Cover cover) {
    return jdbc.queryForObject(
        "select version from flowboard.task where id=?", Long.class, cover.taskId());
  }

  protected int count(String table) {
    return jdbc.queryForObject(
        "select count(*) from " + table + " where tenant_id=?", Integer.class, tenant);
  }

  protected int attempts(UUID key) {
    return jdbc.queryForObject(
        "select count(*) from flowboard.task_transition_attempt where tenant_id=? and"
            + " idempotency_key=?",
        Integer.class,
        tenant,
        key.toString());
  }

  protected void assertNoSettlement() {
    assertThat(count("production.prod_work_order")).isZero();
    assertThat(count("sales_ord.order_cover_result")).isZero();
    assertThat(count("sales_ord.order_cover_line_result")).isZero();
  }

  protected <T> T tx(Supplier<T> work) {
    return new TransactionTemplate(transactions).execute(status -> work.get());
  }

  protected <T> T inActor(Supplier<T> work) {
    authenticate(actor);
    try {
      return work.get();
    } finally {
      clearOrderCoverContext();
    }
  }

  protected record Cover(UUID orderId, UUID caseId, UUID taskId, List<UUID> lineIds) {}

  protected record ReservationStock(UUID locationId, UUID productId) {}
}
