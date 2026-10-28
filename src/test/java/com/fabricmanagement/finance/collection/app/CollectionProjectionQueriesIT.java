package com.fabricmanagement.finance.collection.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.finance.collection.domain.CollectionProjection;
import com.fabricmanagement.finance.collection.infra.repository.CollectionProjectionRepository;
import com.fabricmanagement.testsupport.AbstractIntegrationTest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Real PostgreSQL: confirming orders as unchanged moves only their check time, only forward, and
 * only for the calling tenant's named orders; the order-id range a reconciliation page is judged
 * against follows the database's own ordering; and the read and change times persist.
 */
class CollectionProjectionQueriesIT extends AbstractIntegrationTest {
  @Autowired CollectionProjectionService service;
  @Autowired CollectionProjectionRepository projections;
  @Autowired TransactionTemplate transactions;

  @Autowired
  @Qualifier("systemJdbcTemplate")
  JdbcTemplate systemJdbc;

  UUID tenant, otherTenant, actor, named, untouched, foreign, neverChecked;
  final UUID plan = UUID.randomUUID(), revision = UUID.randomUUID();
  final Instant earlier = Instant.parse("2026-10-20T12:00:00Z");
  final Instant listed = Instant.parse("2026-10-23T08:00:00Z");

  @BeforeEach
  void seed() {
    tenant = UUID.randomUUID();
    otherTenant = UUID.randomUUID();
    actor = UUID.randomUUID();
    named = UUID.randomUUID();
    untouched = UUID.randomUUID();
    foreign = UUID.randomUUID();
    neverChecked = UUID.randomUUID();
    in(
        tenant,
        () -> {
          save(named, earlier);
          save(untouched, earlier);
          save(neverChecked, earlier);
          return null;
        });
    // A full read is always recorded; only the listing check time can still be unknown. The
    // system template commits on its own connection, so it runs once the rows above are committed.
    systemJdbc.update(
        "update finance.collection_projection set checked_at = null"
            + " where tenant_id = ? and order_id = ?",
        tenant,
        neverChecked);
    in(
        otherTenant,
        () -> {
          save(foreign, earlier);
          return null;
        });
  }

  @Test
  void onlyTheNamedOrdersCheckTimeMovesAndNothingElseAboutThem() {
    long versionBefore = in(tenant, () -> find(named).getVersion());
    in(
        tenant,
        () -> {
          service.confirmUnchanged(List.of(named), listed);
          return null;
        });
    var row = in(tenant, () -> find(named));
    assertThat(row.getCheckedAt()).isEqualTo(listed);
    assertThat(row.getSourceVersion()).isEqualTo(3L);
    assertThat(row.getSourceOrderVersion()).isEqualTo(7L);
    assertThat(row.getRevisionId()).isEqualTo(revision);
    // The row version stays, so a rebuild that read the row meanwhile is not made to fail.
    assertThat(row.getVersion()).isEqualTo(versionBefore);
    assertThat(in(tenant, () -> find(untouched).getCheckedAt())).isEqualTo(earlier);
  }

  @Test
  void anOlderListingNeverOverwritesANewerCheck() {
    in(
        tenant,
        () -> {
          service.confirmUnchanged(List.of(named), listed);
          service.confirmUnchanged(List.of(named), earlier.plusSeconds(60));
          return null;
        });
    assertThat(in(tenant, () -> find(named).getCheckedAt())).isEqualTo(listed);
  }

  @Test
  void aProjectionThatWasNeverCheckedGetsItsFirstCheckTime() {
    in(
        tenant,
        () -> {
          service.confirmUnchanged(List.of(neverChecked), listed);
          return null;
        });
    assertThat(in(tenant, () -> find(neverChecked).getCheckedAt())).isEqualTo(listed);
  }

  @Test
  void anotherTenantsProjectionIsNeverTouched() {
    in(
        tenant,
        () -> {
          service.confirmUnchanged(List.of(foreign), listed);
          return null;
        });
    var row = in(otherTenant, () -> projections.findByTenantIdAndOrderId(otherTenant, foreign));
    assertThat(row).get().extracting(CollectionProjection::getCheckedAt).isEqualTo(earlier);
  }

  @Test
  void theRangeIsOpenAtTheStartClosedAtTheEndAndInDatabaseOrder() {
    var all =
        in(
            tenant,
            () ->
                projections
                    .findByTenantIdAndOrderIdGreaterThanOrderByOrderId(
                        tenant, new UUID(0, 0), PageRequest.of(0, 50))
                    .stream()
                    .map(CollectionProjection::getOrderId)
                    .toList());
    assertThat(all).hasSize(3);
    var range =
        in(
            tenant,
            () ->
                projections
                    .findByTenantIdAndOrderIdGreaterThanAndOrderIdLessThanEqualOrderByOrderId(
                        tenant, all.get(0), all.get(2))
                    .stream()
                    .map(CollectionProjection::getOrderId)
                    .toList());
    assertThat(range).containsExactly(all.get(1), all.get(2));
    var empty =
        in(
            tenant,
            () ->
                projections
                    .findByTenantIdAndOrderIdGreaterThanAndOrderIdLessThanEqualOrderByOrderId(
                        tenant, all.get(1), all.get(1)));
    assertThat(empty).isEmpty();
  }

  @Test
  void theRangeNeverReturnsAnotherTenantsProjection() {
    var seen =
        in(
            tenant,
            () ->
                projections
                    .findByTenantIdAndOrderIdGreaterThanAndOrderIdLessThanEqualOrderByOrderId(
                        tenant, new UUID(0, 0), new UUID(-1L, -1L)));
    assertThat(seen).extracting(CollectionProjection::getOrderId).doesNotContain(foreign);
  }

  @Test
  void theReadTimeFollowsEveryReadWhileTheChangeTimeOnlyFollowsRealChanges() {
    UUID order = UUID.randomUUID();
    Instant first = Instant.parse("2026-10-01T10:00:00Z");
    Instant second = Instant.parse("2026-10-02T10:00:00Z");
    Instant third = Instant.parse("2026-10-03T10:00:00Z");
    in(
        tenant,
        () -> {
          var row = CollectionProjection.create(order, plan);
          row.observed(revision, 3, 7L, false, false, first);
          projections.saveAndFlush(row);
          return null;
        });
    in(
        tenant,
        () -> {
          var row = find(order);
          row.observed(revision, 3, 7L, false, false, second);
          projections.saveAndFlush(row);
          return null;
        });
    var unchanged = in(tenant, () -> find(order));
    assertThat(unchanged.getSourceReadAt()).isEqualTo(second);
    assertThat(unchanged.getSourceChangedAt()).isEqualTo(first);
    in(
        tenant,
        () -> {
          var row = find(order);
          row.observed(revision, 3, 8L, false, false, third);
          projections.saveAndFlush(row);
          return null;
        });
    var moved = in(tenant, () -> find(order));
    assertThat(moved.getSourceReadAt()).isEqualTo(third);
    assertThat(moved.getSourceChangedAt()).isEqualTo(third);
  }

  private CollectionProjection find(UUID order) {
    return projections.findByTenantIdAndOrderId(tenant, order).orElseThrow();
  }

  private void save(UUID order, Instant checkedAt) {
    var row = CollectionProjection.create(order, plan);
    row.observed(revision, 3, 7L, false, false, checkedAt);
    projections.saveAndFlush(row);
  }

  private <T> T in(UUID asTenant, Supplier<T> work) {
    return TenantContext.executeInTenantContext(
        asTenant,
        () -> {
          TenantContext.setCurrentUserId(actor);
          return transactions.execute(status -> work.get());
        });
  }
}
