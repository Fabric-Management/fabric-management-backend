package com.fabricmanagement.platform.tradingpartner.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fabricmanagement.common.infrastructure.persistence.SystemTransactionExecutor;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.platform.tradingpartner.domain.ChequeDrawerType;
import com.fabricmanagement.platform.tradingpartner.dto.TradingPartnerChequeDrawerAddResultDto.Outcome;
import com.fabricmanagement.testsupport.AbstractFabricAppIntegrationTest;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * PAYMENT-PLAN-7 R13, R15 against PostgreSQL, connected as the real runtime role {@code fabric_app}
 * (NOBYPASSRLS), so row-level security and the table grants are exercised, not bypassed.
 */
class TradingPartnerChequeDrawerIT extends AbstractFabricAppIntegrationTest {
  @Autowired TradingPartnerChequeDrawerService drawers;
  @Autowired SystemTransactionExecutor system;
  @Autowired TransactionTemplate transactions;
  JdbcTemplate tenantJdbc;

  /** The primary (fabric_app) connection, bound to the tenant of the surrounding transaction. */
  @Autowired
  void tenantDataSource(@Qualifier("dataSource") DataSource dataSource) {
    tenantJdbc = new JdbcTemplate(dataSource);
  }

  UUID tenant, otherTenant, customer, supplier;

  @BeforeEach
  void seed() {
    tenant = UUID.randomUUID();
    otherTenant = UUID.randomUUID();
    customer = UUID.randomUUID();
    supplier = UUID.randomUUID();
    system.executeInTransaction(
        sql -> {
          for (UUID id : List.of(tenant, otherTenant))
            sql.update(
                "insert into common_tenant.common_tenant(id,uid,slug,name,status) values"
                    + " (?,?,?,'Northbridge Cloth','ACTIVE')",
                id,
                id.toString(),
                id.toString());
          for (Object[] partner :
              List.of(
                  new Object[] {customer, "CUSTOMER", "Pennine Weaving"},
                  new Object[] {supplier, "SUPPLIER", "Calder Yarns"})) {
            UUID registry = UUID.randomUUID();
            sql.update(
                "insert into common_company.trading_partner_registry(id,uid,official_name,country)"
                    + " values (?,?,?,'GBR')",
                registry,
                registry.toString(),
                partner[2]);
            sql.update(
                "insert into common_company.common_trading_partner"
                    + "(id,tenant_id,uid,registry_id,partner_type) values (?,?,?,?,?)",
                partner[0],
                tenant,
                partner[0].toString(),
                registry,
                partner[1]);
          }
          return null;
        });
  }

  <T> T in(UUID scope, Supplier<T> work) {
    return TenantContext.executeInTenantContext(
        scope,
        () ->
            transactions.execute(
                status -> {
                  TenantContext.setCurrentUserId(UUID.randomUUID());
                  return work.get();
                }));
  }

  @Test
  void aNameIsRecordedOnceAndAnInactiveOneIsOnlyReactivatedOnRequest() {
    var created =
        in(tenant, () -> drawers.add(customer, "Oliver Hartley", ChequeDrawerType.INDIVIDUAL));
    assertThat(created.outcome()).isEqualTo(Outcome.CREATED);

    var again =
        in(tenant, () -> drawers.add(customer, "  oliver  HARTLEY", ChequeDrawerType.INDIVIDUAL));
    assertThat(again.outcome()).isEqualTo(Outcome.EXISTING);
    assertThat(again.drawer().id()).isEqualTo(created.drawer().id());

    in(tenant, () -> drawers.deactivate(customer, created.drawer().id()));
    assertThat(in(tenant, () -> drawers.list(customer, false))).isEmpty();
    assertThat(in(tenant, () -> drawers.list(customer, true)))
        .singleElement()
        .satisfies(row -> assertThat(row.active()).isFalse());

    var inactive =
        in(tenant, () -> drawers.add(customer, "Oliver Hartley", ChequeDrawerType.INDIVIDUAL));
    assertThat(inactive.outcome()).isEqualTo(Outcome.INACTIVE_DUPLICATE);
    assertThat(inactive.drawer().active()).isFalse();

    var reactivated = in(tenant, () -> drawers.reactivate(customer, created.drawer().id()));
    assertThat(reactivated.id()).isEqualTo(created.drawer().id());
    assertThat(in(tenant, () -> drawers.list(customer, false)))
        .extracting(row -> row.id())
        .containsExactly(created.drawer().id());
  }

  @Test
  void theDatabaseRefusesASecondRowWithTheSameNormalisedName() {
    in(tenant, () -> drawers.add(customer, "Hartley Holdings", ChequeDrawerType.COMPANY));
    assertThatThrownBy(
            () ->
                system.executeInTransaction(
                    sql ->
                        sql.update(
                            "insert into common_company.trading_partner_cheque_drawer"
                                + "(tenant_id,uid,partner_id,name,normalized_name,drawer_type)"
                                + " values (?,?,?,'HARTLEY HOLDINGS','hartley holdings','COMPANY')",
                            tenant,
                            UUID.randomUUID().toString(),
                            customer)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void anotherTenantCannotSeeOrChangeTheList() {
    var created =
        in(tenant, () -> drawers.add(customer, "Oliver Hartley", ChequeDrawerType.INDIVIDUAL));
    assertThatThrownBy(() -> in(otherTenant, () -> drawers.list(customer, true)))
        .isInstanceOf(NotFoundException.class);
    assertThatThrownBy(
            () -> in(otherTenant, () -> drawers.deactivate(customer, created.drawer().id())))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void renamingKeepsTheRowAndRefusesAnotherDrawersName() {
    var ava = in(tenant, () -> drawers.add(customer, "Ava Pryce", ChequeDrawerType.INDIVIDUAL));
    in(tenant, () -> drawers.add(customer, "Oliver Hartley", ChequeDrawerType.INDIVIDUAL));

    var renamed =
        in(
            tenant,
            () ->
                drawers.change(
                    customer, ava.drawer().id(), "Ava Pryce-Hughes", ChequeDrawerType.INDIVIDUAL));
    assertThat(renamed.id()).isEqualTo(ava.drawer().id());
    assertThat(renamed.name()).isEqualTo("Ava Pryce-Hughes");
    assertThatThrownBy(
            () ->
                in(
                    tenant,
                    () ->
                        drawers.change(
                            customer,
                            ava.drawer().id(),
                            "oliver hartley",
                            ChequeDrawerType.INDIVIDUAL)))
        .hasMessageContaining("has this name");
  }

  @Test
  void aSupplierOnlyPartnerGetsNoDrawers() {
    assertThatThrownBy(
            () -> in(tenant, () -> drawers.add(supplier, "Ava Pryce", ChequeDrawerType.INDIVIDUAL)))
        .hasMessageContaining("only to a customer");
  }

  @Test
  void rowLevelSecurityHidesAndRefusesAnotherTenantsDrawers() {
    in(tenant, () -> drawers.add(customer, "Oliver Hartley", ChequeDrawerType.INDIVIDUAL));
    assertThat(
            in(
                tenant,
                () ->
                    tenantJdbc.queryForObject(
                        "select count(*) from common_company.trading_partner_cheque_drawer",
                        Integer.class)))
        .isEqualTo(1);
    assertThat(
            in(
                otherTenant,
                () ->
                    tenantJdbc.queryForObject(
                        "select count(*) from common_company.trading_partner_cheque_drawer",
                        Integer.class)))
        .isZero();
    assertThatThrownBy(
            () ->
                in(
                    otherTenant,
                    () ->
                        tenantJdbc.update(
                            "insert into common_company.trading_partner_cheque_drawer"
                                + "(tenant_id,uid,partner_id,name,normalized_name,drawer_type)"
                                + " values (?,?,?,'Ava Pryce','ava pryce','INDIVIDUAL')",
                            tenant,
                            UUID.randomUUID().toString(),
                            customer)))
        .isInstanceOf(DataAccessException.class);
  }

  @Test
  void theApplicationRoleCannotDeleteADrawer() {
    in(tenant, () -> drawers.add(customer, "Oliver Hartley", ChequeDrawerType.INDIVIDUAL));
    assertThatThrownBy(
            () ->
                in(
                    tenant,
                    () ->
                        tenantJdbc.update(
                            "delete from common_company.trading_partner_cheque_drawer")))
        .isInstanceOf(DataAccessException.class);
  }
}
