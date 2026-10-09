package com.fabricmanagement.sales.salesorder.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.dto.PermissionResult;
import com.fabricmanagement.common.infrastructure.web.exception.DomainException;
import com.fabricmanagement.common.infrastructure.web.exception.NotFoundException;
import com.fabricmanagement.platform.organization.domain.Department;
import com.fabricmanagement.platform.organization.domain.Organization;
import com.fabricmanagement.platform.organization.domain.OrganizationType;
import com.fabricmanagement.platform.organization.infra.repository.DepartmentRepository;
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
import com.fabricmanagement.platform.user.domain.UserDepartment;
import com.fabricmanagement.platform.user.infra.repository.RoleRepository;
import com.fabricmanagement.platform.user.infra.repository.UserDepartmentRepository;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import com.fabricmanagement.sales.salesorder.domain.OrderStatus;
import com.fabricmanagement.sales.salesorder.domain.SalesOrder;
import com.fabricmanagement.sales.salesorder.domain.SalesOrderLine;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditBase;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditLeaseDtos;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditOperationView;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditOutcome;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderEditResult;
import com.fabricmanagement.sales.salesorder.dto.SalesOrderLineResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;

/**
 * Tenant, actor and order binding of the safe edit (CEDIT-02 §8, S3.5, S7.4, S10.9, S11.1, S16.8)
 * and the real row-level security and keys of its three tables (CEDIT-03 §4.2, §7).
 *
 * <p>Permissions here are split per action: withdrawing only {@code sales:write} keeps the order
 * readable, so a refused save is a 403 and not the 404 of an unreadable order. A withdrawal can
 * also be limited to the fresh evaluation while the cached one still grants write, which proves
 * that saving and opening consult the FRESH permission.
 *
 * <p>The application connects as a superuser, which RLS never restricts; tenant isolation of the
 * new tables is therefore proven through {@link #appConnection(UUID)}, the NOBYPASSRLS {@code
 * fabric_app} role, never by what the owner connection can see.
 */
class SalesOrderEditAccessIT extends SalesOrderEditItSupport {

  private static final List<String> EDIT_TABLES =
      List.of("order_edit_base", "order_edit_operation", "order_field_change");
  private static final String RLS_VIOLATION = "42501";
  private static final String FOREIGN_KEY_VIOLATION = "23503";
  private static final String UNIQUE_VIOLATION = "23505";

  @Autowired private TenantRepository tenantRepository;
  @Autowired private OrganizationRepository organizationRepository;
  @Autowired private RoleRepository roleRepository;
  @Autowired private DepartmentRepository departmentRepository;
  @Autowired private UserRepository userRepository;
  @Autowired private UserDepartmentRepository userDepartmentRepository;
  @Autowired private TradingPartnerRegistryRepository registryRepository;
  @Autowired private TradingPartnerRepository partnerRepository;

  @PersistenceContext private EntityManager entityManager;

  /** Users whose write permission is withdrawn in both the fresh and the cached evaluation. */
  private final Set<UUID> writeWithdrawn = ConcurrentHashMap.newKeySet();

  /** Users whose write is withdrawn only in the fresh evaluation; the cache still grants it. */
  private final Set<UUID> writeWithdrawnFreshOnly = ConcurrentHashMap.newKeySet();

  /** Runs inside the caller's transaction just before a fresh permission evaluation answers. */
  private volatile Consumer<UUID> beforeFreshCheck = user -> {};

  /** An order of a tenant with one line, and the actor who works on it. */
  private record OrderRef(Actor actor, UUID orderId, UUID lineId) {}

  @BeforeEach
  void stubPermissionsPerAction() {
    writeWithdrawn.clear();
    writeWithdrawnFreshOnly.clear();
    beforeFreshCheck = user -> {};
    when(permissionEvaluator.evaluate(any(), any(), any(), any()))
        .thenAnswer(invocation -> permissionsOf(invocation.getArgument(3), false));
    when(permissionEvaluator.evaluateFresh(any(), any(), any(), any()))
        .thenAnswer(
            invocation -> {
              UUID user = invocation.getArgument(3);
              if (user != null) {
                beforeFreshCheck.accept(user);
              }
              return permissionsOf(user, true);
            });
  }

  // ── §8 and the permission scenarios ───────────────────────────────────────

  @Test
  @DisplayName("S8.1: sent to planning while the form was open; save is 409 ORDER_WITH_PLANNING")
  void orderWithPlanningRefusesTheSave() {
    SalesOrderEditBase base = open(actorB);
    long version = orderVersion();
    sendToPlanning();

    Object result = save(actorB, body(UUID.randomUUID(), base.baseId(), "notes", set("Urgent")));

    assertThat(failureCode(result)).isEqualTo("ORDER_WITH_PLANNING");
    assertThat(((DomainException) result).getHttpStatus()).isEqualTo(409);
    assertThat(receipts()).isZero();
    assertThat(orderVersion()).isEqualTo(version);
    assertThat(orderText("notes")).isNull();
  }

  @Test
  @DisplayName("S8.2: write withdrawn, cache still grants it; the FRESH check refuses with 403")
  void withdrawnWriteIsSeenFresh() {
    SalesOrderEditBase base = open(actorB);
    long version = orderVersion();
    int bases = basesOf(orderId);
    withdrawWrite(actorB, true);

    UUID operationId = UUID.randomUUID();
    Object result = save(actorB, body(operationId, base.baseId(), "notes", set("Urgent")));

    assertThat(result).isInstanceOf(AccessDeniedException.class);
    assertThat(receiptsFor(operationId)).isZero();
    assertThat(receipts()).isZero();
    assertThat(basesOf(orderId)).isEqualTo(bases);
    assertThat(orderVersion()).isEqualTo(version);
    assertThat(orderText("notes")).isNull();
  }

  @Test
  @DisplayName("S8.2: without any sales permission the order is unreadable; the save is 404")
  void noSalesPermissionAtAllIsNotFound() {
    SalesOrderEditBase base = open(actorB);
    revoke(actorB);

    Object result = save(actorB, body(UUID.randomUUID(), base.baseId(), "notes", set("Urgent")));

    assertThat(result).isInstanceOf(NotFoundException.class);
    assertThat(receipts()).isZero();
  }

  @Test
  @DisplayName("S8.3: another user's base of the same order is EDIT_BASE_UNKNOWN and stays usable")
  void anotherActorsBaseIsUnknown() {
    SalesOrderEditBase baseOfA = open(actorA);
    long version = orderVersion();

    Object result = save(actorB, body(UUID.randomUUID(), baseOfA.baseId(), "notes", set("Urgent")));

    assertThat(failureCode(result)).isEqualTo("EDIT_BASE_UNKNOWN");
    assertThat(((DomainException) result).getHttpStatus()).isEqualTo(409);
    assertThat(receipts()).isZero();
    assertThat(orderVersion()).isEqualTo(version);

    SalesOrderEditResult own =
        saved(actorA, body(UUID.randomUUID(), baseOfA.baseId(), "notes", set("Urgent")));
    assertThat(own.outcome()).isEqualTo(SalesOrderEditOutcome.APPLIED);
  }

  @Test
  @DisplayName("S8.4: a tenant-1 base on the tenant-2 user's own order is EDIT_BASE_UNKNOWN")
  void baseOfAnotherTenantIsUnknown() {
    SalesOrderEditBase baseOfTenant1 = open(actorA);
    OrderRef other = secondTenantOrder();
    long otherVersion = versionOf(other.orderId());

    Object result =
        saveOn(
            other.actor(),
            other.orderId(),
            body(UUID.randomUUID(), baseOfTenant1.baseId(), "notes", set("Urgent")));

    assertThat(failureCode(result)).isEqualTo("EDIT_BASE_UNKNOWN");
    assertThat(receiptsOn(other.orderId())).isZero();
    assertThat(versionOf(other.orderId())).isEqualTo(otherVersion);
    assertThat(receipts()).isZero();
  }

  @Test
  @DisplayName("S8.5: a tenant-2 user on a tenant-1 order path is 404 for open, save and read")
  void orderOfAnotherTenantIsNotFound() {
    SalesOrderEditBase baseOfA = open(actorA);
    SalesOrderEditResult savedByA =
        saved(actorA, body(UUID.randomUUID(), baseOfA.baseId(), "notes", set("Urgent")));
    OrderRef other = secondTenantOrder();
    SalesOrderEditBase otherBase = (SalesOrderEditBase) openOn(other.actor(), other.orderId());
    long version = orderVersion();
    int receipts = receipts();

    assertThat(openOn(other.actor(), orderId)).isInstanceOf(NotFoundException.class);
    assertThat(
            saveOn(
                other.actor(),
                orderId,
                body(UUID.randomUUID(), otherBase.baseId(), "notes", set("Leeds"))))
        .isInstanceOf(NotFoundException.class);
    assertThat(
            saveOn(
                other.actor(),
                orderId,
                body(UUID.randomUUID(), baseOfA.baseId(), "notes", set("Leeds"))))
        .isInstanceOf(NotFoundException.class);
    assertThat(operationOn(other.actor(), orderId, savedByA.operationId()))
        .isInstanceOf(NotFoundException.class);

    assertThat(orderVersion()).isEqualTo(version);
    assertThat(receipts()).isEqualTo(receipts);
    assertThat(basesOf(orderId)).isEqualTo(2);
    assertThat(orderText("notes")).isEqualTo("Urgent");
  }

  @Test
  @DisplayName("S8.6: the base of order O1 on the path of order O2 is EDIT_BASE_UNKNOWN")
  void baseOfAnotherOrderIsUnknown() {
    SalesOrderEditBase baseOfO1 = open(actorA);
    OrderRef o2 = sameTenantOrder();
    long o2Version = versionOf(o2.orderId());

    Object result =
        saveOn(
            actorA,
            o2.orderId(),
            body(UUID.randomUUID(), baseOfO1.baseId(), "notes", set("Urgent")));

    assertThat(failureCode(result)).isEqualTo("EDIT_BASE_UNKNOWN");
    assertThat(receiptsOn(o2.orderId())).isZero();
    assertThat(versionOf(o2.orderId())).isEqualTo(o2Version);
    assertThat(receipts()).isZero();
  }

  @Test
  @DisplayName("S8.7: a receipt is read only by its own actor on its own order; otherwise 404")
  void receiptIsOnlyForItsActorAndOrder() {
    SalesOrderEditBase base = open(actorA);
    SalesOrderEditResult result =
        saved(actorA, body(UUID.randomUUID(), base.baseId(), "notes", set("Urgent")));
    OrderRef o2 = sameTenantOrder();
    OrderRef other = secondTenantOrder();

    Object own = operationOn(actorA, orderId, result.operationId());
    assertThat(own).isInstanceOf(SalesOrderEditOperationView.class);
    SalesOrderEditOperationView view = (SalesOrderEditOperationView) own;
    assertThat(view.operationId()).isEqualTo(result.operationId());
    assertThat(view.outcome()).isEqualTo(SalesOrderEditOutcome.APPLIED);
    assertThat(view.resultVersion()).isEqualTo(result.resultVersion());
    assertThat(view.conflictBaseId()).isNull();

    assertThat(operationOn(actorB, orderId, result.operationId()))
        .isInstanceOf(NotFoundException.class);
    assertThat(operationOn(actorA, o2.orderId(), result.operationId()))
        .isInstanceOf(NotFoundException.class);
    assertThat(operationOn(other.actor(), orderId, result.operationId()))
        .isInstanceOf(NotFoundException.class);
    assertThat(operationOn(other.actor(), other.orderId(), result.operationId()))
        .isInstanceOf(NotFoundException.class);
    assertThat(operationOn(actorA, orderId, UUID.randomUUID()))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  @DisplayName("S8.8: opening without write is 403; permission comes before the editable state")
  void openingWithoutWriteIsForbidden() {
    withdrawWrite(actorB, true);
    assertThat(openAs(actorB)).isInstanceOf(AccessDeniedException.class);

    // Write withdrawn and the order not editable: the permission still answers first.
    sendToPlanning();
    assertThat(openAs(actorB)).isInstanceOf(AccessDeniedException.class);

    assertThat(basesOf(orderId)).isZero();
  }

  @Test
  @DisplayName("S8.8: opening a non-draft order is 409; content lock is checked before status")
  void openingANonDraftOrderIsAConflict() {
    jdbc.update(
        "UPDATE sales_ord.sales_order SET status = 'PENDING_APPROVAL' WHERE id = ?", orderId);
    Object statusRefused = openAs(actorA);
    assertThat(failureCode(statusRefused)).isEqualTo("ORDER_RULE_VIOLATION");
    assertThat(((DomainException) statusRefused).getHttpStatus()).isEqualTo(409);

    // With planning as well: the commercial content lock is checked before the status.
    sendToPlanning();
    Object lockRefused = openAs(actorA);
    assertThat(failureCode(lockRefused)).isEqualTo("ORDER_WITH_PLANNING");
    assertThat(((DomainException) lockRefused).getHttpStatus()).isEqualTo(409);

    assertThat(basesOf(orderId)).isZero();
  }

  @Test
  @DisplayName("S3.5: a save that would be NO_CHANGE is refused with 403 once write is withdrawn")
  void noOpDoesNotSkipAuthorization() {
    SalesOrderEditBase baseOfA = open(actorA);
    SalesOrderEditBase baseOfB = open(actorB);
    saved(actorB, body(UUID.randomUUID(), baseOfB.baseId(), "paymentTerms", set("60 days")));
    long version = orderVersion();
    withdrawWrite(actorA, false);

    UUID operationId = UUID.randomUUID();
    Map<String, Object> request =
        body(operationId, baseOfA.baseId(), "paymentTerms", set("60 days"));
    Object result = save(actorA, request);

    assertThat(result).isInstanceOf(AccessDeniedException.class);
    assertThat(receiptsFor(operationId)).isZero();
    assertThat(receipts()).isEqualTo(1);
    assertThat(orderVersion()).isEqualTo(version);

    // The same request with write restored is the S3.2 no-op: the 403 was the permission alone.
    restoreWrite(actorA);
    SalesOrderEditResult noOp = saved(actorA, request);
    assertThat(noOp.outcome()).isEqualTo(SalesOrderEditOutcome.NO_CHANGE);
    assertThat(orderVersion()).isEqualTo(version);
  }

  @Test
  @DisplayName("S10.9: a committed save repeated after write was withdrawn is 403; nothing changes")
  void replayAfterWithdrawnWriteIsForbidden() {
    SalesOrderEditBase base = open(actorB);
    UUID operationId = UUID.randomUUID();
    UUID clientLineId = UUID.randomUUID();
    Map<String, Object> request =
        withLines(
            body(operationId, base.baseId(), "notes", set("Urgent")),
            List.of(
                add(
                    clientLineId,
                    UUID.randomUUID(),
                    "quantity",
                    set(quantity("200", "M")),
                    "pricing",
                    set(pricing("GBP", "4.2500")))));
    SalesOrderEditResult first = saved(actorB, request);
    assertThat(first.outcome()).isEqualTo(SalesOrderEditOutcome.APPLIED);
    long version = orderVersion();
    int history = historyRows();
    int lines = activeLines();
    withdrawWrite(actorB, false);

    Object repeated = save(actorB, request);

    assertThat(repeated).isInstanceOf(AccessDeniedException.class);
    assertThat(receipts()).isEqualTo(1);
    assertThat(receiptsFor(operationId)).isEqualTo(1);
    assertThat(orderVersion()).isEqualTo(version);
    assertThat(historyRows()).isEqualTo(history);
    assertThat(activeLines()).isEqualTo(lines);

    // Control: with write back the same repeat is answered from its receipt.
    restoreWrite(actorB);
    SalesOrderEditResult replay = saved(actorB, request);
    assertThat(replay.replayed()).isTrue();
    assertThat(replay.resultVersion()).isEqualTo(first.resultVersion());
    assertThat(replay.lineIds()).isEqualTo(first.lineIds());
  }

  @Test
  @DisplayName("S16.8: a specification save with a profile input is 403 before any resolution")
  void profileSaveWithoutWriteIsForbidden() {
    SalesOrderEditBase base = open(actorB);
    long version = orderVersion();
    long l1Version = lineVersion(l1);
    withdrawWrite(actorB, false);

    UUID operationId = UUID.randomUUID();
    Object result =
        save(
            actorB,
            withLines(
                body(operationId, base.baseId()),
                List.of(update(l1, "specification", set(specificationWithProfile())))));

    assertThat(result).isInstanceOf(AccessDeniedException.class);
    assertThat(receiptsFor(operationId)).isZero();
    assertThat(receipts()).isZero();
    assertThat(profileVersions()).isZero();
    assertThat(lineVersion(l1)).isEqualTo(l1Version);
    assertThat(orderVersion()).isEqualTo(version);
    assertThat(historyRows()).isZero();
  }

  @Test
  @DisplayName(
      "S7.4: UPDATE of a random line id and of another order's line answer the same (lease"
          + " required, CEDIT-07-F3)")
  void lineNotInBaseIsTheSameForUnknownAndForeignLines() {
    SalesOrderEditBase base = open(actorA);
    OrderRef o2 = sameTenantOrder();
    long version = orderVersion();
    long foreignLineVersion = lineVersion(o2.lineId());
    UUID randomLine = UUID.randomUUID();

    Object unknown =
        save(
            actorA,
            withLines(
                body(UUID.randomUUID(), base.baseId()),
                List.of(update(randomLine, "pricing", set(pricing("GBP", "4.5000"))))));
    Object foreign =
        save(
            actorA,
            withLines(
                body(UUID.randomUUID(), base.baseId()),
                List.of(update(o2.lineId(), "pricing", set(pricing("GBP", "4.5000"))))));

    // Leases are always enforced (CEDIT-07-F3): neither line is a line of this order, so neither
    // can be leased and both saves stop at the proof, before the base is read for them.
    assertThat(failureCode(unknown)).isEqualTo("EDIT_LEASE_REQUIRED");
    assertThat(failureCode(foreign)).isEqualTo("EDIT_LEASE_REQUIRED");
    // The same answer apart from naming the line the request itself sent.
    assertThat(answerOf((DomainException) foreign)).isEqualTo(answerOf((DomainException) unknown));
    assertThat(((DomainException) unknown).getHttpStatus()).isEqualTo(409);
    assertThat(requiredLines((DomainException) unknown)).containsExactly(randomLine);
    assertThat(requiredLines((DomainException) foreign)).containsExactly(o2.lineId());

    assertThat(receipts()).isZero();
    assertThat(receiptsOn(o2.orderId())).isZero();
    assertThat(orderVersion()).isEqualTo(version);
    assertThat(lineVersion(o2.lineId())).isEqualTo(foreignLineVersion);
    assertThat(historyRows()).isZero();
  }

  @Test
  @DisplayName("S11.1: a random base id is EDIT_BASE_UNKNOWN and nothing is written")
  void randomBaseIsUnknown() {
    open(actorA);
    long version = orderVersion();
    int bases = basesOf(orderId);

    Object result =
        save(actorA, body(UUID.randomUUID(), UUID.randomUUID(), "notes", set("Urgent")));

    assertThat(failureCode(result)).isEqualTo("EDIT_BASE_UNKNOWN");
    assertThat(((DomainException) result).getHttpStatus()).isEqualTo(409);
    assertThat(receipts()).isZero();
    assertThat(basesOf(orderId)).isEqualTo(bases);
    assertThat(orderVersion()).isEqualTo(version);
    assertThat(orderText("notes")).isNull();
  }

  // ── base snapshot (CEDIT-03 §4.1) ─────────────────────────────────────────

  @Test
  @DisplayName("S11.6: the base is one REPEATABLE READ snapshot while a line change commits")
  void baseIsOneRepeatableReadSnapshot() {
    long l1VersionBefore = lineVersion(l1);
    AtomicBoolean armed = new AtomicBoolean(true);
    AtomicReference<String> isolation = new AtomicReference<>();
    ExecutorService writer = Executors.newSingleThreadExecutor();
    // The fresh write check runs after the order was read and before the lines are read: the
    // other transaction commits exactly there, deterministically.
    beforeFreshCheck =
        user -> {
          if (!user.equals(actorA.id()) || !armed.compareAndSet(true, false)) {
            return;
          }
          isolation.set(
              String.valueOf(
                  entityManager
                      .createNativeQuery("SELECT current_setting('transaction_isolation')")
                      .getSingleResult()));
          Future<Integer> committed =
              writer.submit(
                  () ->
                      jdbc.update(
                          "UPDATE sales_ord.sales_order_line SET requested_qty = 900,"
                              + " version = version + 1 WHERE id = ?",
                          l1));
          awaitCommit(committed);
        };
    SalesOrderEditBase base;
    try {
      base = open(actorA);
    } finally {
      beforeFreshCheck = user -> {};
      writer.shutdownNow();
    }

    assertThat(armed).isFalse();
    assertThat(isolation.get()).isEqualTo("repeatable read");
    // The change committed before the lines were read, yet the base shows the state of its own
    // snapshot: both in the order the form shows and in the stored base content.
    assertThat(lineQuantity(base, l1)).isEqualByComparingTo("1000");
    assertThat(new BigDecimal(baseLineValue(base.baseId(), l1, "{quantity,requestedQty}")))
        .isEqualByComparingTo("1000");
    assertThat(Long.parseLong(baseLineValue(base.baseId(), l1, "{lineVersion}")))
        .isEqualTo(l1VersionBefore);

    // Control: the change is committed, and a base opened now sees it.
    assertThat(lineVersion(l1)).isEqualTo(l1VersionBefore + 1);
    SalesOrderEditBase later = open(actorA);
    assertThat(lineQuantity(later, l1)).isEqualByComparingTo("900");
    assertThat(Long.parseLong(baseLineValue(later.baseId(), l1, "{lineVersion}")))
        .isEqualTo(l1VersionBefore + 1);
  }

  // ── real RLS of the three new tables (fabric_app, NOBYPASSRLS) ────────────

  @Test
  @DisplayName("CEDIT-03 RLS: the app role sees only its tenant's bases, receipts and history")
  void appRoleSeesOnlyItsTenant() throws SQLException {
    SalesOrderEditBase base = open(actorA);
    saved(actorA, body(UUID.randomUUID(), base.baseId(), "notes", set("Urgent")));
    OrderRef other = secondTenantOrder();
    SalesOrderEditBase otherBase = (SalesOrderEditBase) openOn(other.actor(), other.orderId());
    Object otherSave =
        saveOn(
            other.actor(),
            other.orderId(),
            body(UUID.randomUUID(), otherBase.baseId(), "notes", set("York")));
    assertThat(otherSave).isInstanceOf(SalesOrderEditResult.class);
    UUID tenant2 = other.actor().tenantId();

    for (String table : EDIT_TABLES) {
      int ownRows = ownerCount(table, tenantId);
      int otherRows = ownerCount(table, tenant2);
      assertThat(ownRows).as("%s rows of tenant 1", table).isPositive();
      assertThat(otherRows).as("%s rows of tenant 2", table).isPositive();

      try (Connection app = appConnection(tenantId)) {
        assertThat(count(app, "SELECT count(*) FROM sales_ord." + table))
            .as("%s visible to tenant 1", table)
            .isEqualTo(ownRows);
        assertThat(
                count(
                    app,
                    "SELECT count(*) FROM sales_ord." + table + " WHERE tenant_id = ?",
                    tenant2))
            .as("%s rows of tenant 2 seen by tenant 1", table)
            .isZero();
        assertThat(
                count(
                    app,
                    "SELECT count(*) FROM sales_ord." + table + " WHERE sales_order_id = ?",
                    other.orderId()))
            .isZero();
      }
      try (Connection app = appConnection(tenant2)) {
        assertThat(count(app, "SELECT count(*) FROM sales_ord." + table))
            .as("%s visible to tenant 2", table)
            .isEqualTo(otherRows);
        assertThat(
                count(
                    app,
                    "SELECT count(*) FROM sales_ord." + table + " WHERE tenant_id = ?",
                    tenantId))
            .isZero();
      }
      try (Connection app = appConnection(null)) {
        assertThat(count(app, "SELECT count(*) FROM sales_ord." + table))
            .as("%s without app.current_tenant", table)
            .isZero();
      }
    }
  }

  @Test
  @DisplayName("CEDIT-03 RLS: WITH CHECK refuses rows of another tenant and rows without a tenant")
  void appRoleCannotWriteAnotherTenantsRows() throws SQLException {
    OrderRef other = secondTenantOrder();
    UUID tenant2 = other.actor().tenantId();
    UUID otherActor = other.actor().id();

    try (Connection app = appConnection(tenantId)) {
      assertSqlState(
          () ->
              insertBase(
                  app, UUID.randomUUID(), tenant2, other.orderId(), otherActor, "OPENED", null),
          RLS_VIOLATION,
          "row-level security");
      assertSqlState(
          () -> insertReceipt(app, tenant2, other.orderId(), otherActor, UUID.randomUUID()),
          RLS_VIOLATION,
          "row-level security");
      assertSqlState(
          () -> insertHistory(app, tenant2, other.orderId(), null, otherActor),
          RLS_VIOLATION,
          "row-level security");

      // Control: the same rows in the connection's own tenant are accepted.
      insertBase(app, UUID.randomUUID(), tenantId, orderId, actorA.id(), "OPENED", null);
      insertReceipt(app, tenantId, orderId, actorA.id(), UUID.randomUUID());
      insertHistory(app, tenantId, orderId, null, actorA.id());
    }
    try (Connection app = appConnection(null)) {
      assertSqlState(
          () -> insertBase(app, UUID.randomUUID(), tenantId, orderId, actorA.id(), "OPENED", null),
          RLS_VIOLATION,
          "row-level security");
      assertSqlState(
          () -> insertReceipt(app, tenantId, orderId, actorA.id(), UUID.randomUUID()),
          RLS_VIOLATION,
          "row-level security");
      assertSqlState(
          () -> insertHistory(app, tenantId, orderId, null, actorA.id()),
          RLS_VIOLATION,
          "row-level security");
    }
    for (String table : EDIT_TABLES) {
      assertThat(ownerCount(table, tenant2)).as("%s rows of tenant 2", table).isZero();
    }
  }

  // ── keys of the new tables ────────────────────────────────────────────────

  @Test
  @DisplayName("CEDIT-03 FK: a history row's line must be a line of the same order and tenant")
  void historyLineBelongsToItsOrder() throws SQLException {
    OrderRef o2 = sameTenantOrder();

    try (Connection app = appConnection(tenantId)) {
      assertSqlState(
          () -> insertHistory(app, tenantId, orderId, o2.lineId(), actorA.id()),
          FOREIGN_KEY_VIOLATION,
          "fk_order_field_change_line");
      assertSqlState(
          () -> insertHistory(app, tenantId, orderId, UUID.randomUUID(), actorA.id()),
          FOREIGN_KEY_VIOLATION,
          "fk_order_field_change_line");
      // Control: the order's own line is accepted.
      insertHistory(app, tenantId, orderId, l1, actorA.id());
    }
    assertThat(historyRows()).isEqualTo(1);
  }

  @Test
  @DisplayName("CEDIT-03 FK: a base's origin receipt is checked at commit, in either write order")
  void originReceiptForeignKeyIsDeferred() throws SQLException {
    UUID missingOperation = UUID.randomUUID();
    UUID orphanBase = UUID.randomUUID();
    try (Connection app = appConnection(tenantId)) {
      app.setAutoCommit(false);
      // Accepted inside the transaction: the key is deferred to the commit.
      insertBase(app, orphanBase, tenantId, orderId, actorA.id(), "CONFLICT", missingOperation);
      assertSqlState(app::commit, FOREIGN_KEY_VIOLATION, "fk_order_edit_base_origin_operation");
    }
    assertThat(baseExists(orphanBase)).isFalse();

    UUID operationId = UUID.randomUUID();
    UUID conflictBase = UUID.randomUUID();
    try (Connection app = appConnection(tenantId)) {
      app.setAutoCommit(false);
      // The service writes the conflict base before its receipt; the commit accepts that order.
      insertBase(app, conflictBase, tenantId, orderId, actorA.id(), "CONFLICT", operationId);
      insertReceipt(app, tenantId, orderId, actorA.id(), operationId);
      app.commit();
    }
    assertThat(baseExists(conflictBase)).isTrue();
    assertThat(receiptsFor(operationId)).isEqualTo(1);
  }

  @Test
  @DisplayName("CEDIT-03 unique: a client line id is unique per order among removed lines too")
  void clientLineIdIsUniqueAcrossInactiveLines() {
    OrderRef o2 = sameTenantOrder();
    UUID clientLineId = UUID.randomUUID();
    jdbc.update(
        "UPDATE sales_ord.sales_order_line SET client_line_id = ?, is_active = false WHERE id = ?",
        clientLineId,
        l1);

    assertThatThrownBy(
            () ->
                jdbc.update(
                    "UPDATE sales_ord.sales_order_line SET client_line_id = ? WHERE id = ?",
                    clientLineId,
                    l2))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("uq_sales_order_line_client_line");

    // Control: the same client id on a line of another order is a different identity.
    assertThat(
            jdbc.update(
                "UPDATE sales_ord.sales_order_line SET client_line_id = ? WHERE id = ?",
                clientLineId,
                o2.lineId()))
        .isEqualTo(1);
  }

  @Test
  @DisplayName("CEDIT-03 unique: an operation id is unique per tenant across orders only")
  void operationIdIsUniquePerTenant() throws SQLException {
    OrderRef o2 = sameTenantOrder();
    OrderRef other = secondTenantOrder();
    UUID operationId = UUID.randomUUID();

    try (Connection app = appConnection(tenantId)) {
      insertReceipt(app, tenantId, orderId, actorA.id(), operationId);
      assertSqlState(
          () -> insertReceipt(app, tenantId, o2.orderId(), actorA.id(), operationId),
          UNIQUE_VIOLATION,
          "uq_order_edit_operation_tenant_operation");
    }
    try (Connection app = appConnection(other.actor().tenantId())) {
      insertReceipt(
          app, other.actor().tenantId(), other.orderId(), other.actor().id(), operationId);
    }
    assertThat(receiptsFor(operationId)).isEqualTo(2);
    assertThat(receiptsOn(o2.orderId())).isZero();
  }

  // ── permissions per action ────────────────────────────────────────────────

  private PermissionResult permissionsOf(UUID userId, boolean fresh) {
    DataScope scope = userId == null ? null : scopes.get(userId);
    if (scope == null) {
      return new PermissionResult(Map.of(), false);
    }
    boolean writable =
        !writeWithdrawn.contains(userId) && !(fresh && writeWithdrawnFreshOnly.contains(userId));
    Map<String, DataScope> actions =
        writable ? Map.of("read", scope, "write", scope) : Map.of("read", scope);
    return new PermissionResult(Map.of("sales", actions), false);
  }

  /** Takes away write only; read stays. {@code freshOnly} leaves a stale cached grant behind. */
  private void withdrawWrite(Actor actor, boolean freshOnly) {
    (freshOnly ? writeWithdrawnFreshOnly : writeWithdrawn).add(actor.id());
    clearPermissionCache();
  }

  private void restoreWrite(Actor actor) {
    writeWithdrawn.remove(actor.id());
    writeWithdrawnFreshOnly.remove(actor.id());
    clearPermissionCache();
  }

  private void clearPermissionCache() {
    Cache cache = cacheManager.getCache("permissions");
    if (cache != null) {
      cache.clear();
    }
  }

  // ── calls on any order ────────────────────────────────────────────────────

  private Object openAs(Actor actor) {
    return openOn(actor, orderId);
  }

  private Object openOn(Actor actor, UUID order) {
    return as(actor, () -> edits.openBase(order, actor.id(), actor.authentication()));
  }

  private Object operationOn(Actor actor, UUID order, UUID operationId) {
    return as(actor, () -> edits.operation(order, operationId, actor.id()));
  }

  /** A failure as the client sees it, without the line the request itself named. */
  private static Map<String, Object> answerOf(DomainException failure) {
    Map<String, Object> answer = new LinkedHashMap<>();
    answer.put("code", failure.getErrorCode());
    answer.put("status", failure.getHttpStatus());
    answer.put("message", failure.getMessage());
    Map<String, Object> details = new LinkedHashMap<>(failure.getDetails());
    details.remove("lineId");
    if (details.get("leases") instanceof List<?> requirements) {
      details.put(
          "leases",
          requirements.stream()
              .map(
                  requirement ->
                      requirement instanceof SalesOrderEditLeaseDtos.Requirement named
                          ? new SalesOrderEditLeaseDtos.Requirement(
                              named.key(), null, named.reason(), named.holder())
                          : requirement)
              .toList());
    }
    answer.put("details", details);
    return answer;
  }

  /** The lines a lease-required answer names. */
  private static List<UUID> requiredLines(DomainException failure) {
    assertThat(failure.getDetails().get("leases")).isInstanceOf(List.class);
    List<?> requirements = (List<?>) failure.getDetails().get("leases");
    return requirements.stream()
        .map(requirement -> ((SalesOrderEditLeaseDtos.Requirement) requirement).lineId())
        .toList();
  }

  /** A specification value with a line-explicit requirement profile input and no facets. */
  private Map<String, Object> specificationWithProfile() {
    Map<String, Object> basis = new LinkedHashMap<>();
    basis.put("kind", "LINE_EXPLICIT");
    basis.put("actorId", actorB.id());
    basis.put("decidedAt", "2026-10-01T09:00:00Z");
    basis.put("decisionReference", "Customer e-mail from Jane Hill");
    Map<String, Object> profile = new LinkedHashMap<>();
    profile.put("basis", basis);
    profile.put("scopeVersion", "1");
    profile.put("resolutionRuleVersion", "1");
    Map<String, Object> specification = new LinkedHashMap<>();
    specification.put("moduleSpecs", Map.of("gsm", 180));
    specification.put("requirementProfile", profile);
    return specification;
  }

  private static BigDecimal lineQuantity(SalesOrderEditBase base, UUID lineId) {
    return base.order().getLines().stream()
        .filter(line -> line.getId().equals(lineId))
        .map(SalesOrderLineResponse::getRequestedQty)
        .findFirst()
        .orElseThrow(() -> new AssertionError("Line " + lineId + " is not in the base's order"));
  }

  /** A value of one line in the stored base content, by JSON path below the line. */
  private String baseLineValue(UUID baseId, UUID lineId, String path) {
    return jdbc.queryForObject(
        "SELECT line #>> ?::text[] FROM sales_ord.order_edit_base b"
            + " CROSS JOIN LATERAL jsonb_array_elements(b.content -> 'lines') AS line"
            + " WHERE b.id = ? AND line ->> 'lineId' = ?",
        String.class,
        path,
        baseId,
        lineId.toString());
  }

  private static void awaitCommit(Future<?> committed) {
    try {
      committed.get(20, TimeUnit.SECONDS);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(interrupted);
    } catch (ExecutionException | TimeoutException failure) {
      throw new IllegalStateException("The concurrent line change did not commit", failure);
    }
  }

  // ── fixtures: a second order, a second tenant ─────────────────────────────

  /** Order O2 of the same tenant and customer, with one line. */
  private OrderRef sameTenantOrder() {
    TenantContext.setCurrentTenantId(tenantId);
    TenantContext.setCurrentUserId(actorA.id());
    try {
      UUID order = createOrder(partnerId, UUID.randomUUID().toString().substring(0, 8));
      return new OrderRef(actorA, order, createLine(order, p2));
    } finally {
      TenantContext.clear();
    }
  }

  /** Tenant 2 with one seller (GLOBAL sales read/write), a customer and an order with one line. */
  private OrderRef secondTenantOrder() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    UUID tenant = tenantRepository.save(Tenant.create("Edit " + suffix, "SE-" + suffix)).getId();
    TenantContext.setCurrentTenantId(tenant);
    try {
      Organization organization =
          organizationRepository.save(
              Organization.create("Edit Org " + suffix, "TAX-" + suffix, OrganizationType.WEAVER));
      Role role = roleRepository.save(Role.create("Seller " + suffix, "WORKER", "Safe edit test"));
      Department sales =
          departmentRepository.save(
              Department.create(organization.getId(), "Sales", "SALES", "Sales"));
      User user = User.create("York", "Seller", organization.getId());
      user.setRole(role);
      user = userRepository.save(user);
      userDepartmentRepository.save(UserDepartment.create(user, sales, true, user.getId()));
      Actor actor = new Actor(tenant, user.getId(), List.of(sales.getDepartmentCode()));
      scopes.put(actor.id(), DataScope.GLOBAL);

      TenantContext.setCurrentUserId(actor.id());
      String name = "Edit customer " + suffix;
      TradingPartnerRegistry registry = TradingPartnerRegistry.create(null, name, "GBR");
      registry.setUid("REG-" + UUID.randomUUID());
      UUID partner =
          partnerRepository
              .saveAndFlush(
                  TradingPartner.create(
                      registryRepository.save(registry), PartnerType.CUSTOMER, name))
              .getId();
      UUID order = createOrder(partner, suffix);
      return new OrderRef(actor, order, createLine(order, UUID.randomUUID()));
    } finally {
      TenantContext.clear();
    }
  }

  private UUID createOrder(UUID partner, String suffix) {
    SalesOrder order =
        SalesOrder.builder()
            .tradingPartnerId(partner)
            .orderNumber("SO-E2-" + suffix)
            .status(OrderStatus.DRAFT)
            .orderDate(ORDER_DATE)
            .paymentTerms("30 days")
            .contactName("Jane Hill")
            .contactEmail("jane@example.com")
            .build();
    return orders.saveAndFlush(order).getId();
  }

  private UUID createLine(UUID order, UUID product) {
    SalesOrderLine line =
        SalesOrderLine.builder()
            .salesOrderId(order)
            .productId(product)
            .requestedQty(new BigDecimal("700"))
            .unit("M")
            .currency("GBP")
            .unitPriceAmount(new BigDecimal("5.0000"))
            .build();
    return lines.saveAndFlush(line).getId();
  }

  // ── database reads (owner connection) ─────────────────────────────────────

  private long versionOf(UUID order) {
    return jdbc.queryForObject(
        "SELECT version FROM sales_ord.sales_order WHERE id = ?", Long.class, order);
  }

  private int receiptsOn(UUID order) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM sales_ord.order_edit_operation WHERE sales_order_id = ?",
        Integer.class,
        order);
  }

  private int receiptsFor(UUID operationId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM sales_ord.order_edit_operation WHERE operation_id = ?",
        Integer.class,
        operationId);
  }

  private int basesOf(UUID order) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM sales_ord.order_edit_base WHERE sales_order_id = ?",
        Integer.class,
        order);
  }

  private boolean baseExists(UUID baseId) {
    Integer found =
        jdbc.queryForObject(
            "SELECT count(*) FROM sales_ord.order_edit_base WHERE id = ?", Integer.class, baseId);
    return found != null && found > 0;
  }

  private int ownerCount(String table, UUID tenant) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM sales_ord." + table + " WHERE tenant_id = ?", Integer.class, tenant);
  }

  // ── SQL as the application role ───────────────────────────────────────────

  private static int count(Connection connection, String sql, Object... arguments)
      throws SQLException {
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      for (int i = 0; i < arguments.length; i++) {
        statement.setObject(i + 1, arguments[i]);
      }
      try (ResultSet rows = statement.executeQuery()) {
        rows.next();
        return rows.getInt(1);
      }
    }
  }

  private static void insertBase(
      Connection connection,
      UUID id,
      UUID tenant,
      UUID order,
      UUID actor,
      String origin,
      UUID originOperation)
      throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO sales_ord.order_edit_base (id, tenant_id, created_at, updated_at,"
                + " sales_order_id, actor_id, order_version, content, origin,"
                + " origin_operation_id, captured_at, expires_at)"
                + " VALUES (?, ?, now(), now(), ?, ?, 0, '{}'::jsonb, ?, ?, now(),"
                + " now() + interval '1 hour')")) {
      statement.setObject(1, id);
      statement.setObject(2, tenant);
      statement.setObject(3, order);
      statement.setObject(4, actor);
      statement.setString(5, origin);
      if (originOperation == null) {
        statement.setNull(6, Types.OTHER);
      } else {
        statement.setObject(6, originOperation);
      }
      statement.executeUpdate();
    }
  }

  private static void insertReceipt(
      Connection connection, UUID tenant, UUID order, UUID actor, UUID operationId)
      throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO sales_ord.order_edit_operation (id, tenant_id, created_at, updated_at,"
                + " operation_id, sales_order_id, actor_id, base_id, request_fingerprint,"
                + " outcome, result_version, result_base_id, recorded_at)"
                + " VALUES (?, ?, now(), now(), ?, ?, ?, ?, ?, 'NO_CHANGE', 0, ?, now())")) {
      statement.setObject(1, UUID.randomUUID());
      statement.setObject(2, tenant);
      statement.setObject(3, operationId);
      statement.setObject(4, order);
      statement.setObject(5, actor);
      statement.setObject(6, UUID.randomUUID());
      statement.setString(7, "0".repeat(64));
      statement.setObject(8, UUID.randomUUID());
      statement.executeUpdate();
    }
  }

  private static void insertHistory(
      Connection connection, UUID tenant, UUID order, UUID lineId, UUID actor) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement(
            "INSERT INTO sales_ord.order_field_change (id, tenant_id, created_at, updated_at,"
                + " sales_order_id, operation_id, operation_receipt_id, line_id, edit_key,"
                + " change_kind, new_value, actor_id, order_version, changed_at)"
                + " VALUES (?, ?, now(), now(), ?, ?, ?, ?, ?, 'SET', '\"Leeds\"'::jsonb, ?, 1,"
                + " now())")) {
      statement.setObject(1, UUID.randomUUID());
      statement.setObject(2, tenant);
      statement.setObject(3, order);
      statement.setObject(4, UUID.randomUUID());
      statement.setObject(5, UUID.randomUUID());
      if (lineId == null) {
        statement.setNull(6, Types.OTHER);
      } else {
        statement.setObject(6, lineId);
      }
      statement.setString(7, lineId == null ? "notes" : "line.productDesc");
      statement.setObject(8, actor);
      statement.executeUpdate();
    }
  }

  private static void assertSqlState(ThrowingCallable call, String sqlState, String text) {
    assertThatThrownBy(call)
        .isInstanceOfSatisfying(
            SQLException.class,
            failure -> {
              assertThat(failure.getSQLState()).as("%s", failure.getMessage()).isEqualTo(sqlState);
              assertThat(failure.getMessage()).contains(text);
            });
  }
}
