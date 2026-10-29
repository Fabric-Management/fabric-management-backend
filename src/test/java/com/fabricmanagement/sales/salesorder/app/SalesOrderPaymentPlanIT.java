package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fabricmanagement.common.infrastructure.persistence.SystemTransactionExecutor;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanContent;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanInput;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanTypes.DateTrigger;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanTypes.Frequency;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanTypes.Kind;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanTypes.Method;
import com.fabricmanagement.sales.salesorder.dto.CreateSalesOrderRequest;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderPaymentPlanDtos.Save;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderRepository;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

/** Real PostgreSQL transactions, version locks, normalised storage and a non-owner RLS role. */
class SalesOrderPaymentPlanIT extends AbstractIntegrationTest {
  @Autowired SalesOrderPaymentPlanService plans;
  @Autowired SalesOrderService salesOrders;
  @Autowired PaymentPlanStore store;
  @Autowired SalesOrderRepository orders;
  @Autowired SystemTransactionExecutor system;
  @Autowired TransactionTemplate transactions;
  @Autowired JdbcTemplate jdbc;
  JdbcTemplate tenantJdbc;

  /**
   * The only JdbcTemplate bean is the system one, which never joins this test's transactions; the
   * RLS probe needs the primary DataSource so SET LOCAL ROLE applies to the query that follows.
   */
  @Autowired
  void tenantDataSource(@Qualifier("dataSource") DataSource dataSource) {
    tenantJdbc = new JdbcTemplate(dataSource);
  }

  @Autowired EntityManager em;
  @MockitoBean SalesOrderEditAccess access;
  @MockitoBean OrderIntakeHooks intake;
  @MockitoBean com.fabricmanagement.sales.orderintake.app.StockSelectionLifecycleService selections;

  @MockitoBean
  com.fabricmanagement.flowboard.generator.app.SalesStockPlanningTaskService stockTasks;

  UUID tenant, otherTenant, orderId, partner, actor;

  @BeforeEach
  void seed() {
    tenant = UUID.randomUUID();
    otherTenant = UUID.randomUUID();
    orderId = UUID.randomUUID();
    partner = UUID.randomUUID();
    actor = UUID.randomUUID();
    UUID registry = UUID.randomUUID();
    system.executeInTransaction(
        sql -> {
          for (UUID id : List.of(tenant, otherTenant))
            sql.update(
                "insert into common_tenant.common_tenant(id,uid,slug,name,status) values"
                    + " (?,?,?,'Northbridge Cloth','ACTIVE')",
                id,
                id.toString(),
                id.toString());
          sql.update(
              "insert into common_company.trading_partner_registry(id,uid,official_name,country)"
                  + " values (?,?,'Pennine Textiles','GBR')",
              registry,
              registry.toString());
          sql.update(
              "insert into"
                  + " common_company.common_trading_partner(id,tenant_id,uid,registry_id,partner_type)"
                  + " values (?,?,?,?,'CUSTOMER')",
              partner,
              tenant,
              partner.toString(),
              registry);
          sql.update(
              "insert into"
                  + " sales_ord.sales_order(id,tenant_id,uid,trading_partner_id,order_number,order_date)"
                  + " values (?,?,?,?,?,'2026-10-20')",
              orderId,
              tenant,
              orderId.toString(),
              partner,
              "SO-" + orderId);
          sql.update(
              "insert into"
                  + " sales_ord.sales_order_line(id,tenant_id,uid,created_at,updated_at,sales_order_id,product_id,product_desc,requested_qty,initial_requested_qty,unit,unit_price,currency)"
                  + " values (?,?,?,now(),now(),?,?,'Cotton poplin',100,100,'M',1,'GBP')",
              UUID.randomUUID(),
              tenant,
              UUID.randomUUID().toString(),
              orderId,
              UUID.randomUUID());
          return null;
        });
    when(access.requireWritable(any(), eq(actor)))
        .thenAnswer(
            invocation ->
                orders
                    .findByTenantIdAndId(TenantContext.requireTenantId(), invocation.getArgument(0))
                    .orElseThrow(() -> new NotFoundException("Order not found")));
    when(access.requireReadable(any(), eq(actor)))
        .thenAnswer(
            invocation -> {
              orders
                  .findByTenantIdAndId(TenantContext.requireTenantId(), invocation.getArgument(0))
                  .orElseThrow(() -> new NotFoundException("Order not found"));
              return mock(SalesOrderRepository.LiveRevisionView.class);
            });
  }

  <T> T in(UUID scope, Supplier<T> work) {
    return TenantContext.executeInTenantContext(
        scope,
        () ->
            transactions.execute(
                status -> {
                  TenantContext.setCurrentUserId(actor);
                  return work.get();
                }));
  }

  PaymentPlanInput input() {
    var rules =
        new PaymentPlanInput.Rules(
            Frequency.MONTH,
            3,
            LocalDate.of(2026, 10, 30),
            20,
            Method.CASH,
            null,
            null,
            null,
            null,
            null,
            null);
    return new PaymentPlanInput(List.of(new PaymentPlanInput.CurrencyInput("GBP", rules, null)));
  }

  Save first(UUID operation) {
    return new Save(operation, 0L, 0L, input(), "Initial schedule");
  }

  @Test
  void persistsAcrossPersistenceContextsAndReplaysWithoutAnotherRevision() {
    UUID operation = UUID.randomUUID();
    var saved = in(tenant, () -> plans.save(orderId, first(operation), actor));
    var restored = in(tenant, () -> plans.read(orderId, actor));
    assertThat(restored.draft().id()).isEqualTo(saved.id());
    assertThat(restored.draft().content().currencyGroups().getFirst().lines())
        .extracting(PaymentPlanContent.Line::expectedDate)
        .containsExactly(
            LocalDate.of(2026, 10, 30), LocalDate.of(2026, 11, 20), LocalDate.of(2026, 12, 20));
    var replay = in(tenant, () -> plans.save(orderId, first(operation), actor));
    assertThat(replay.id()).isEqualTo(saved.id());
    assertThat(
            in(
                tenant,
                () ->
                    plans
                        .history(
                            orderId, actor, org.springframework.data.domain.PageRequest.of(0, 10))
                        .getTotalElements()))
        .isEqualTo(1L);
    assertThatThrownBy(() -> in(otherTenant, () -> plans.read(orderId, actor)))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void twoConcurrentRepeatsCreateOnlyOneRevision() throws Exception {
    var pool = Executors.newFixedThreadPool(2);
    UUID operation = UUID.randomUUID();
    var start = new CountDownLatch(1);
    try {
      Callable<UUID> save =
          () -> {
            start.await();
            return in(tenant, () -> plans.save(orderId, first(operation), actor)).id();
          };
      var one = pool.submit(save);
      var two = pool.submit(save);
      start.countDown();
      assertThat(one.get(20, TimeUnit.SECONDS)).isEqualTo(two.get(20, TimeUnit.SECONDS));
      assertThat(
              in(
                  tenant,
                  () ->
                      plans
                          .history(
                              orderId, actor, org.springframework.data.domain.PageRequest.of(0, 10))
                          .getTotalElements()))
          .isEqualTo(1L);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void failedPlanCreationRollsBackTheOrderAsWell() {
    var request = new CreateSalesOrderRequest();
    request.setPartnerId(partner);
    request.setOrderDate(LocalDate.of(2026, 10, 20));
    request.setLines(List.of());
    request.setPaymentPlan(
        new PaymentPlanInput(
            List.of(
                new PaymentPlanInput.CurrencyInput(
                    "GBP",
                    null,
                    List.of(
                        new PaymentPlanInput.Row(
                            null,
                            Kind.INSTALMENT,
                            new BigDecimal("-1"),
                            Method.CASH,
                            DateTrigger.EXPLICIT_DATE,
                            LocalDate.of(2026, 10, 30),
                            null))))));
    int before =
        in(
            tenant,
            () ->
                jdbc.queryForObject(
                    "select count(*) from sales_ord.sales_order where tenant_id=?",
                    Integer.class,
                    tenant));
    assertThatThrownBy(() -> in(tenant, () -> salesOrders.createOrder(request)))
        .hasMessageContaining("Invalid payment plan");
    assertThat(
            in(
                tenant,
                () ->
                    jdbc.queryForObject(
                        "select count(*) from sales_ord.sales_order where tenant_id=?",
                        Integer.class,
                        tenant)))
        .isEqualTo(before);
    assertThat(
            in(
                tenant,
                () ->
                    jdbc.queryForObject(
                        "select count(*) from sales_ord.order_payment_plan where tenant_id=?",
                        Integer.class,
                        tenant)))
        .isZero();
  }

  @Test
  void changingOrderAmountsMarksThePlanStaleInsteadOfRewritingItsRows() {
    var saved = in(tenant, () -> plans.save(orderId, first(UUID.randomUUID()), actor));
    in(
        tenant,
        () -> {
          jdbc.update(
              "update sales_ord.sales_order_line set unit_price=2 where tenant_id=? and"
                  + " sales_order_id=?",
              tenant,
              orderId);
          return null;
        });
    var current = in(tenant, () -> plans.read(orderId, actor));
    assertThat(current.stale()).isTrue();
    assertThatThrownBy(
            () ->
                in(
                    tenant,
                    () ->
                        plans.snapshot(
                            orders.findByTenantIdAndId(tenant, orderId).orElseThrow(), true)))
        .hasMessageContaining("Reconcile the payment plan");
    assertThat(current.draft().content()).isEqualTo(saved.content());
    assertThat(current.capabilities())
        .anySatisfy(
            capability -> {
              assertThat(capability.action()).isEqualTo("SUBMIT_FOR_APPROVAL");
              assertThat(capability.allowed()).isFalse();
              assertThat(capability.reason()).isEqualTo("WRONG_STATUS");
            });
  }

  @Test
  void databaseRlsHidesAnotherTenantsPlanEvenWithoutAWhereClause() {
    in(tenant, () -> plans.save(orderId, first(UUID.randomUUID()), actor));
    system.executeInTransaction(
        sql -> {
          sql.execute(
              "DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE"
                  + " rolname='payment_plan_rls_probe') THEN CREATE ROLE payment_plan_rls_probe"
                  + " NOLOGIN NOBYPASSRLS; END IF; END $$");
          sql.execute("GRANT USAGE ON SCHEMA sales_ord TO payment_plan_rls_probe");
          sql.execute(
              "GRANT SELECT, INSERT ON sales_ord.order_payment_plan TO payment_plan_rls_probe");
          return null;
        });
    in(
        tenant,
        () -> {
          tenantJdbc.execute("SET LOCAL ROLE payment_plan_rls_probe");
          tenantJdbc.execute("SET LOCAL row_security=on");
          assertThat(
                  tenantJdbc.queryForObject(
                      "select count(*) from sales_ord.order_payment_plan", Integer.class))
              .isEqualTo(1);
          return null;
        });
    in(
        otherTenant,
        () -> {
          tenantJdbc.execute("SET LOCAL ROLE payment_plan_rls_probe");
          tenantJdbc.execute("SET LOCAL row_security=on");
          assertThat(
                  tenantJdbc.queryForObject(
                      "select count(*) from sales_ord.order_payment_plan", Integer.class))
              .isZero();
          return null;
        });
    assertThatThrownBy(
            () ->
                in(
                    otherTenant,
                    () -> {
                      tenantJdbc.execute("SET LOCAL ROLE payment_plan_rls_probe");
                      tenantJdbc.execute("SET LOCAL row_security=on");
                      tenantJdbc.update(
                          "insert into"
                              + " sales_ord.order_payment_plan(id,tenant_id,created_at,updated_at,sales_order_id)"
                              + " values (?,?,now(),now(),?)",
                          UUID.randomUUID(),
                          tenant,
                          orderId);
                      return null;
                    }))
        .isInstanceOf(org.springframework.dao.DataAccessException.class);
  }
}
