package com.fabricmanagement.platform.user.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.platform.organization.domain.Organization;
import com.fabricmanagement.platform.organization.domain.OrganizationType;
import com.fabricmanagement.platform.organization.infra.repository.OrganizationRepository;
import com.fabricmanagement.platform.tenant.domain.Tenant;
import com.fabricmanagement.platform.tenant.infra.repository.TenantRepository;
import com.fabricmanagement.platform.user.domain.User;
import com.fabricmanagement.platform.user.dto.NavPreferencesImportResponse;
import com.fabricmanagement.platform.user.dto.NavPreferencesRequest;
import com.fabricmanagement.platform.user.dto.NavPreferencesResponse;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** No test-wide transaction: fixtures commit before worker transactions begin. */
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser
class UserNavPreferencesImportIntegrationTest extends AbstractIntegrationTest {

  @Autowired private UserNavPreferencesService service;
  @Autowired private TenantRepository tenants;
  @Autowired private OrganizationRepository organizations;
  @Autowired private UserRepository users;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private EntityManager entityManager;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  private Identity identity;

  @BeforeEach
  void setUp() {
    identity = newIdentity();
    useIdentity(identity);
  }

  @AfterEach
  void tearDown() {
    TenantContext.clear();
  }

  @Test
  void absentRowIsImportedAndReturnedInsideApiResponse() throws Exception {
    var request = request("/invoicing", "/sales?tab=quotes");
    mockMvc
        .perform(
            post("/api/v1/common/users/{id}/nav-preferences/import", identity.userId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.imported").value(true))
        .andExpect(jsonPath("$.data.preferences.sortOrder[0]").value("/invoicing"))
        .andExpect(jsonPath("$.data.preferences.sortOrder[1]").value("/sales?tab=quotes"))
        .andExpect(jsonPath("$.data.preferences.hiddenItemIds[0]").value("/admin"));

    useIdentity(identity); // JwtContextInterceptor clears the request's context after completion.
    assertThat(service.getPreferences(identity.tenantId(), identity.userId()))
        .isEqualTo(response(request));
    assertThat(rowCount(identity)).isEqualTo(1);
  }

  @Test
  void existingNonEmptyRowWinsWithoutChangingAnyColumn() {
    var original = request("/sales", "/invoicing");
    var saved = service.upsertPreferences(identity.tenantId(), identity.userId(), original);
    var before = row(identity);

    var imported =
        service.importPreferences(identity.tenantId(), identity.userId(), request("/payments"));

    assertThat(imported.imported()).isFalse();
    assertThat(imported.preferences()).isEqualTo(saved);
    assertThat(row(identity)).isEqualTo(before);
  }

  @Test
  void existingEmptyRowIsStillAnExistingRow() {
    var empty = new NavPreferencesRequest(List.of(), List.of());
    service.upsertPreferences(identity.tenantId(), identity.userId(), empty);
    var before = row(identity);

    var imported =
        service.importPreferences(identity.tenantId(), identity.userId(), request("/invoicing"));

    assertThat(imported.imported()).isFalse();
    assertThat(imported.preferences()).isEqualTo(response(empty));
    assertThat(row(identity)).isEqualTo(before);
  }

  @Test
  void concurrentImportsWaitForTheWinnerAndReturnItsCommittedPreferences() throws Exception {
    var winnerInserted = new CountDownLatch(1);
    var releaseWinner = new CountDownLatch(1);
    var loserStarted = new CountDownLatch(1);
    var winnerTransaction = new AtomicLong();
    var loserTransaction = new AtomicLong();
    var loserPid = new AtomicInteger();
    var executor = Executors.newFixedThreadPool(2);
    try {
      var winner =
          executor.submit(
              () -> {
                useIdentity(identity);
                try {
                  return newTransaction()
                      .execute(
                          ignored -> {
                            winnerTransaction.set(transactionId());
                            var result =
                                service.importPreferences(
                                    identity.tenantId(), identity.userId(), request("/invoicing"));
                            winnerInserted.countDown();
                            awaitLatch(releaseWinner);
                            return result;
                          });
                } finally {
                  TenantContext.clear();
                }
              });
      awaitLatch(winnerInserted);
      var loser =
          executor.submit(
              () -> {
                useIdentity(identity);
                try {
                  return newTransaction()
                      .execute(
                          ignored -> {
                            loserTransaction.set(transactionId());
                            loserPid.set(
                                ((Number)
                                        entityManager
                                            .createNativeQuery("SELECT pg_backend_pid()")
                                            .getSingleResult())
                                    .intValue());
                            loserStarted.countDown();
                            return service.importPreferences(
                                identity.tenantId(), identity.userId(), request("/payments"));
                          });
                } finally {
                  TenantContext.clear();
                }
              });
      awaitLatch(loserStarted);
      // Prove real overlap: the losing INSERT is blocked on the winner's uncommitted row.
      await()
          .atMost(Duration.ofSeconds(10))
          .until(
              () ->
                  Boolean.TRUE.equals(
                      jdbc.queryForObject(
                          "SELECT cardinality(pg_blocking_pids(?)) > 0",
                          Boolean.class,
                          loserPid.get())));
      assertThat(loser.isDone()).isFalse();
      assertThat(winnerTransaction.get()).isNotEqualTo(loserTransaction.get());
      releaseWinner.countDown();

      NavPreferencesImportResponse created = winner.get(10, TimeUnit.SECONDS);
      NavPreferencesImportResponse existing = loser.get(10, TimeUnit.SECONDS);
      assertThat(created.imported()).isTrue();
      assertThat(existing.imported()).isFalse();
      assertThat(existing.preferences()).isEqualTo(created.preferences());
      assertThat(service.getPreferences(identity.tenantId(), identity.userId()))
          .isEqualTo(existing.preferences());
      assertThat(rowCount(identity)).isEqualTo(1);
    } finally {
      releaseWinner.countDown();
      executor.shutdownNow();
      assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }
  }

  @Test
  void nativeInsertPopulatesTheSameIdentifierAndAuditColumnsAsPatch() {
    var other = users.saveAndFlush(User.create("Patch", "User", identity.organizationId()));
    var patchIdentity =
        new Identity(
            identity.tenantId(), identity.tenantUid(), identity.organizationId(), other.getId());
    var request = request("/invoicing");
    service.upsertPreferences(identity.tenantId(), other.getId(), request);
    service.importPreferences(identity.tenantId(), identity.userId(), request);
    var patched = row(patchIdentity);
    var imported = row(identity);

    for (var stored : List.of(patched, imported)) {
      assertThat(stored.get("id")).isInstanceOf(UUID.class);
      assertThat(stored.get("uid").toString())
          .matches(identity.tenantUid() + "-NAVPREF-[0-9A-F]{8}");
      assertThat(stored.get("created_at")).isNotNull();
      assertThat(stored.get("updated_at")).isNotNull();
      assertThat(stored)
          .containsEntry("tenant_id", identity.tenantId())
          .containsEntry("created_by", identity.userId())
          .containsEntry("updated_by", identity.userId())
          .containsEntry("is_active", true)
          .containsEntry("deleted_at", null)
          .containsEntry("version", 0L);
    }
    assertThat(imported.get("id")).isNotEqualTo(patched.get("id"));
    assertThat(imported.get("uid")).isNotEqualTo(patched.get("uid"));
    // Compare every column, excluding only values that must differ between two new rows.
    var comparableImport = new HashMap<>(imported);
    var comparablePatch = new HashMap<>(patched);
    for (var column : List.of("id", "uid", "user_id", "created_at", "updated_at")) {
      comparableImport.remove(column);
      comparablePatch.remove(column);
    }
    assertThat(comparableImport).isEqualTo(comparablePatch);
  }

  @Test
  void importCannotReadOrWriteAnotherTenantsUserPreferences() {
    var other = newIdentity();
    useIdentity(other);
    service.upsertPreferences(other.tenantId(), other.userId(), request("/sales"));
    var before = row(other);

    useIdentity(identity);
    assertThatThrownBy(
            () ->
                service.importPreferences(
                    identity.tenantId(), other.userId(), request("/payments")))
        .isInstanceOf(NotFoundException.class);
    assertThat(service.getPreferences(identity.tenantId(), other.userId()).getSortOrder())
        .isEmpty();
    var imported =
        service.importPreferences(identity.tenantId(), identity.userId(), request("/invoicing"));
    assertThat(imported.imported()).isTrue();
    assertThat(imported.preferences().getSortOrder()).containsExactly("/invoicing");
    useIdentity(other);
    assertThat(row(other)).isEqualTo(before);
  }

  @Test
  void pathIdMustBeTheAuthenticatedUser() throws Exception {
    var anotherUser = users.saveAndFlush(User.create("Other", "User", identity.organizationId()));
    mockMvc
        .perform(
            post("/api/v1/common/users/{id}/nav-preferences/import", anotherUser.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request("/invoicing"))))
        .andExpect(status().isForbidden());

    useIdentity(identity);
    assertThat(
            rowCount(
                new Identity(
                    identity.tenantId(),
                    identity.tenantUid(),
                    identity.organizationId(),
                    anotherUser.getId())))
        .isZero();
    assertThat(rowCount(identity)).isZero();
  }

  private Identity newIdentity() {
    TenantContext.clear();
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    Tenant tenant = Tenant.create("Nav import " + suffix, "NAV-" + suffix);
    tenant.activate("test");
    tenant = tenants.saveAndFlush(tenant);
    TenantContext.setCurrentTenantId(tenant.getId());
    TenantContext.setCurrentTenantUid(tenant.getUid());
    var organization =
        organizations.saveAndFlush(
            Organization.create("Nav import " + suffix, suffix, OrganizationType.VERTICAL_MILL));
    var user = users.saveAndFlush(User.create("Nav", "User", organization.getId()));
    return new Identity(tenant.getId(), tenant.getUid(), organization.getId(), user.getId());
  }

  private void useIdentity(Identity selected) {
    TenantContext.setCurrentTenantId(selected.tenantId());
    TenantContext.setCurrentTenantUid(selected.tenantUid());
    TenantContext.setCurrentUserId(selected.userId());
  }

  private TransactionTemplate newTransaction() {
    var transaction = new TransactionTemplate(transactionManager);
    transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    transaction.setTimeout(30);
    return transaction;
  }

  private long transactionId() {
    assertThat(entityManager.createNativeQuery("SHOW transaction_isolation").getSingleResult())
        .isEqualTo("read committed");
    return ((Number) entityManager.createNativeQuery("SELECT txid_current()").getSingleResult())
        .longValue();
  }

  private static void awaitLatch(CountDownLatch latch) {
    try {
      assertThat(latch.await(20, TimeUnit.SECONDS)).as("transaction coordination").isTrue();
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new AssertionError("Interrupted while coordinating import transactions", ex);
    }
  }

  private Map<String, Object> row(Identity selected) {
    return jdbc.queryForMap(
        "SELECT * FROM common_user.user_nav_preferences WHERE tenant_id = ? AND user_id = ?",
        selected.tenantId(),
        selected.userId());
  }

  private int rowCount(Identity selected) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM common_user.user_nav_preferences WHERE tenant_id = ? AND user_id = ?",
        Integer.class,
        selected.tenantId(),
        selected.userId());
  }

  private NavPreferencesRequest request(String... order) {
    return new NavPreferencesRequest(List.of(order), List.of("/admin"));
  }

  private NavPreferencesResponse response(NavPreferencesRequest request) {
    return new NavPreferencesResponse(request.getSortOrder(), request.getHiddenItemIds());
  }

  private record Identity(UUID tenantId, String tenantUid, UUID organizationId, UUID userId) {}
}
