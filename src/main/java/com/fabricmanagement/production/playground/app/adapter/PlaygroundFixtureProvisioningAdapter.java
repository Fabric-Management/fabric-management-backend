package com.fabricmanagement.production.playground.app.adapter;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.tenant.TenantAccessPort;
import com.fabricmanagement.common.infrastructure.tenant.TenantQueryPort;
import com.fabricmanagement.common.infrastructure.tenant.TenantReference;
import com.fabricmanagement.platform.tenant.domain.port.PlaygroundFixtureProvisioningPort;
import com.fabricmanagement.production.playground.app.PlaygroundFiberFixtureService;
import com.fabricmanagement.production.playground.app.PlaygroundFixtureGate;
import com.fabricmanagement.production.playground.domain.PlaygroundFixtureOrigin;
import java.util.Locale;
import java.util.UUID;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Production's implementation of platform's playground fixture port (FIBER-CATALOG-1 §9). The guard
 * lives here, at the entry point: persisted tenant type and demo flag are re-read and the origin
 * conditions are checked before anything is installed.
 */
@Component
@Slf4j
public class PlaygroundFixtureProvisioningAdapter implements PlaygroundFixtureProvisioningPort {

  static final String INSERT_LEGACY_MARKER =
      """
      INSERT INTO production.playground_fixture_run
          (id, tenant_id, uid, origin, status, is_active, created_at, updated_at, version)
      VALUES (gen_random_uuid(), ?, ?, 'LEGACY_PLAYGROUND_CREATION', 'PENDING', TRUE,
              now(), now(), 0)
      ON CONFLICT (tenant_id) DO NOTHING
      """;

  private final JdbcTemplate systemJdbc;
  private final DataSource systemDataSource;
  private final TenantQueryPort tenantQueryPort;
  private final TenantAccessPort tenantAccessPort;
  private final PlaygroundFiberFixtureService fixtureService;
  private final PlaygroundFixtureGate gate;

  public PlaygroundFixtureProvisioningAdapter(
      @Qualifier("systemJdbcTemplate") JdbcTemplate systemJdbc,
      @Qualifier("systemDataSource") DataSource systemDataSource,
      TenantQueryPort tenantQueryPort,
      TenantAccessPort tenantAccessPort,
      PlaygroundFiberFixtureService fixtureService,
      PlaygroundFixtureGate gate) {
    this.systemJdbc = systemJdbc;
    this.systemDataSource = systemDataSource;
    this.tenantQueryPort = tenantQueryPort;
    this.tenantAccessPort = tenantAccessPort;
    this.fixtureService = fixtureService;
    this.gate = gate;
  }

  @Override
  public void registerLegacyPlaygroundCreation(UUID tenantId) {
    if (!TransactionSynchronizationManager.isActualTransactionActive()
        || TransactionSynchronizationManager.getResource(systemDataSource) == null) {
      throw new IllegalStateException(
          "The legacy playground marker must be written inside the clone's system transaction");
    }
    String type =
        systemJdbc.queryForObject(
            "SELECT type FROM common_tenant.common_tenant WHERE id = ?", String.class, tenantId);
    if (!gate.legacyEligible(tenantId, type)) {
      log.info("Legacy playground marker refused: tenant={} type={}", tenantId, type);
      return;
    }
    String random = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    String uid = "PGFX-" + random.toUpperCase(Locale.ROOT);
    systemJdbc.update(INSERT_LEGACY_MARKER, tenantId, uid);
  }

  @Override
  public void provisionLegacyPlayground(UUID tenantId) {
    String type = tenantQueryPort.findById(tenantId).map(TenantReference::type).orElse(null);
    if (!gate.legacyEligible(tenantId, type)) {
      log.info("Playground fixtures refused (legacy): tenant={} type={}", tenantId, type);
      return;
    }
    TenantContext.executeInTenantContext(
        tenantId,
        () ->
            fixtureService.install(
                tenantId, PlaygroundFixtureOrigin.LEGACY_PLAYGROUND_CREATION, false));
  }

  @Override
  public void provisionRegisterFirstPlayground(RegisterFirstPlaygroundSignup signup) {
    UUID tenantId = signup != null ? signup.tenantId() : null;
    if (tenantId == null || !tenantId.equals(TenantContext.getCurrentTenantIdOrNull())) {
      log.info("Playground fixtures refused (register-first): missing or foreign context");
      return;
    }
    String type = tenantQueryPort.findById(tenantId).map(TenantReference::type).orElse(null);
    boolean demoMode = tenantAccessPort.isDemoMode(tenantId);
    if (!gate.registerFirstEligible(signup, type, demoMode)) {
      log.info(
          "Playground fixtures refused (register-first): tenant={} type={} demoMode={}",
          tenantId,
          type,
          demoMode);
      return;
    }
    fixtureService.install(tenantId, PlaygroundFixtureOrigin.REGISTER_FIRST_SIGNUP, true);
  }
}
