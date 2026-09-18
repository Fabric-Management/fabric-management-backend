package com.fabricmanagement.flowboard.routing.app;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fabricmanagement.flowboard.routing.domain.*;
import com.fabricmanagement.flowboard.routing.domain.event.RoutingFailureOpenedEvent;
import com.fabricmanagement.flowboard.routing.domain.port.out.RoutingNotificationPort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

class RoutingFailureAlertIT extends RoutingIntegrationSupport {
  @MockitoSpyBean private RoutingNotificationPort notifications;
  @Autowired private IncompleteEventPublications publications;
  @PersistenceContext private EntityManager entityManager;

  private UUID openFailure() {
    var task = task(user("Creator").getId());
    evaluate(task);
    return failure(task);
  }

  private RoutingRecords.Alert alert(UUID failure, UUID recipient) {
    return repository.alerts(tenant, failure).stream()
        .filter(a -> a.recipientId().equals(recipient))
        .findFirst()
        .orElseThrow();
  }

  private int notificationCount(UUID recipient) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM notification.notification_log WHERE tenant_id = ? AND recipient_id = ? AND event_type = 'ROUTING_FAILURE'",
        Integer.class,
        tenant,
        recipient);
  }

  @Test
  void publicationCommitsWithFailureAndResubmissionRecoversCrashBeforeDelivery() {
    var managerUser = user("Manager", "flowboard:manage-routing");
    seedNotificationTemplate();
    CountDownLatch crashed = new CountDownLatch(1);
    doAnswer(
            invocation -> {
              crashed.countDown();
              throw new IllegalStateException("simulated crash before delivery");
            })
        .when(listenerTarget())
        .onRoutingFailureOpened(any(RoutingFailureOpenedEvent.class));
    UUID failure =
        tx(
            () -> {
              UUID id = openFailure();
              // The Modulith JPA publication is persisted but JDBC does not auto-flush JPA.
              entityManager.flush();
              assertThat(
                      jdbc.queryForObject(
                          "SELECT count(*) FROM event_publication WHERE serialized_event LIKE ?",
                          Integer.class,
                          "%" + id + "%"))
                  .isGreaterThan(0);
              return id;
            });
    await(crashed);
    assertThat(notificationCount(managerUser.getId())).isZero();
    doCallRealMethod()
        .when(listenerTarget())
        .onRoutingFailureOpened(any(RoutingFailureOpenedEvent.class));
    publications.resubmitIncompletePublicationsOlderThan(Duration.ZERO);
    org.awaitility.Awaitility.await()
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(
            () -> {
              assertThat(notificationCount(managerUser.getId())).isEqualTo(1);
              assertThat(alert(failure, managerUser.getId()).status()).isEqualTo("DELIVERED");
              assertThat(
                      jdbc.queryForObject(
                          """
          SELECT count(*) FROM event_publication WHERE serialized_event LIKE ? AND completion_date IS NULL
          """,
                          Integer.class,
                          "%" + failure + "%"))
                  .isZero();
            });
  }

  @Test
  void failureAndPublicationRollbackTogether() {
    var task = task(user("Creator").getId());
    var failureId = new java.util.concurrent.atomic.AtomicReference<UUID>();
    assertThatThrownBy(
            () ->
                tx(
                    () -> {
                      evaluate(task);
                      failureId.set(failure(task));
                      entityManager.flush();
                      assertThat(
                              jdbc.queryForObject(
                                  "SELECT count(*) FROM event_publication WHERE serialized_event LIKE ?",
                                  Integer.class,
                                  "%" + failureId.get() + "%"))
                          .isGreaterThan(0);
                      throw new IllegalStateException("forced failure publication rollback");
                    }))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("forced failure publication rollback");

    assertThat(repository.openFailures(tenant, task.getId())).isEmpty();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM event_publication WHERE serialized_event LIKE ?",
                Integer.class,
                "%" + failureId.get() + "%"))
        .isZero();
  }

  @Test
  void preferenceOffStillDeliversAndDuplicateKeyAndRepairsAreIdempotent() {
    var holder = user("Manager", "flowboard:manage-routing");
    seedNotificationTemplate();
    jdbc.update(
        """
        INSERT INTO notification.user_notification_preference
          (id, tenant_id, uid, user_id, event_type, in_app, email, push)
        VALUES (gen_random_uuid(), ?, gen_random_uuid()::text, ?, 'ROUTING_FAILURE', false, false, false)
        """,
        tenant,
        holder.getId());
    UUID failure = openFailure();
    assertThat(alerts.deliver(tenant, failure)).isTrue();
    var delivered = alert(failure, holder.getId());
    var task = repository.failure(tenant, failure).taskId();
    tx(
        () -> {
          notifications.deliver(tenant, holder.getId(), delivered.id(), failure, task);
          return null;
        });
    assertThat(alerts.deliver(tenant, failure)).isTrue();
    assertThat(notificationCount(holder.getId())).isEqualTo(1);
    assertThat(repository.alerts(tenant, failure)).hasSize(1);
    assertThat(alert(failure, holder.getId()).attempts()).isEqualTo(1);
  }

  @Test
  void missingTemplateIsDurablyFailedVisibleAndRetried() {
    var holder = user("Manager", "flowboard:manage-routing");
    UUID failure = openFailure();
    assertThat(alerts.deliver(tenant, failure)).isFalse();
    assertThat(alert(failure, holder.getId()).status()).isEqualTo("FAILED");
    assertThat(alert(failure, holder.getId()).attempts()).isEqualTo(1);
    assertThat(alert(failure, holder.getId()).lastError()).contains("template missing");
    var response =
        queries.failures(tenant, null, true, org.springframework.data.domain.PageRequest.of(0, 10));
    assertThat(response.getContent().getFirst().alerts().getFirst().lastError())
        .contains("template missing");
    seedNotificationTemplate();
    assertThat(alerts.deliver(tenant, failure)).isTrue();
    assertThat(notificationCount(holder.getId())).isEqualTo(1);
    assertThat(alert(failure, holder.getId()).attempts()).isEqualTo(2);
  }

  @Test
  void emptyHolderSetStaysOwedAndNewHolderGetsDelivery() {
    UUID failure = openFailure();
    assertThat(alerts.deliver(tenant, failure)).isFalse();
    assertThat(repository.alerts(tenant, failure)).isEmpty();
    var holder = user("Manager", "flowboard:manage-routing");
    seedNotificationTemplate();
    assertThat(alerts.deliver(tenant, failure)).isTrue();
    assertThat(notificationCount(holder.getId())).isEqualTo(1);
  }

  @Test
  void concurrentManualAndEventDeliveryProduceOneNotification() throws Exception {
    var holder = user("Manager", "flowboard:manage-routing");
    seedNotificationTemplate();
    CountDownLatch crashed = new CountDownLatch(1);
    doAnswer(
            invocation -> {
              crashed.countDown();
              throw new IllegalStateException("simulated crash before delivery");
            })
        .when(listenerTarget())
        .onRoutingFailureOpened(any(RoutingFailureOpenedEvent.class));
    UUID failure = openFailure();
    await(crashed);
    doCallRealMethod()
        .when(listenerTarget())
        .onRoutingFailureOpened(any(RoutingFailureOpenedEvent.class));
    var results =
        race(
            () -> {
              publications.resubmitIncompletePublicationsOlderThan(Duration.ZERO);
              return true;
            },
            () -> repair.repair(tenant, RoutingPoolKey.ORDER_COVER, false).deliveryComplete());
    assertThat(results).containsOnly(true);
    org.awaitility.Awaitility.await()
        .atMost(Duration.ofSeconds(20))
        .untilAsserted(
            () ->
                assertThat(
                        jdbc.queryForObject(
                            "SELECT count(*) FROM event_publication WHERE serialized_event LIKE ? AND completion_date IS NULL",
                            Integer.class,
                            "%" + failure + "%"))
                    .isZero());
    assertThat(notificationCount(holder.getId())).isEqualTo(1);
    assertThat(repository.alerts(tenant, failure)).hasSize(1);
  }

  @Test
  void errorAfterInsertRollsBackNotificationButOtherRecipientAndFailedAttemptPersist() {
    var a = user("A", "flowboard:manage-routing");
    var b = user("B", "flowboard:manage-routing");
    seedNotificationTemplate();
    UUID failure = openFailure();
    doAnswer(
            invocation -> {
              invocation.callRealMethod();
              throw new IllegalStateException("forced after notification insert");
            })
        .when(notifications)
        .deliver(eq(tenant), eq(a.getId()), any(), eq(failure), any());
    assertThat(alerts.deliver(tenant, failure)).isFalse();
    assertThat(notificationCount(a.getId())).isZero();
    assertThat(alert(failure, a.getId()).status()).isEqualTo("FAILED");
    assertThat(alert(failure, a.getId()).attempts()).isEqualTo(1);
    assertThat(notificationCount(b.getId())).isEqualTo(1);
    assertThat(alert(failure, b.getId()).status()).isEqualTo("DELIVERED");
    doCallRealMethod()
        .when(listenerTarget())
        .onRoutingFailureOpened(any(RoutingFailureOpenedEvent.class));
    RoutingEventListenerTarget.assertIncomplete(
        listener,
        new RoutingFailureOpenedEvent(
            tenant, failure, repository.failure(tenant, failure).taskId()));
    assertThat(notificationCount(a.getId())).isZero();
    assertThat(alert(failure, a.getId()).status()).isEqualTo("FAILED");
    assertThat(alert(failure, b.getId()).status()).isEqualTo("DELIVERED");
  }

  @Test
  void authorityRemovedAfterReconcileCancelsAndRestoredAuthorityDeliversOnce() {
    var a = user("A", "flowboard:manage-routing");
    var b = user("B", "flowboard:manage-routing");
    seedNotificationTemplate();
    UUID failure = openFailure();
    tx(() -> alerts.reconcile(tenant, failure));
    permission(a, "flowboard:manage-routing", false);
    tx(
        () -> {
          alerts.deliverOne(tenant, failure, alert(failure, a.getId()).id(), new int[] {-1});
          return null;
        });
    assertThat(alert(failure, a.getId()).status()).isEqualTo("CANCELLED");
    assertThat(alert(failure, a.getId()).cancelReason()).isEqualTo("RECIPIENT_NOT_ELIGIBLE");
    assertThat(notificationCount(a.getId())).isZero();
    assertThat(alerts.deliver(tenant, failure)).isTrue();
    assertThat(notificationCount(b.getId())).isEqualTo(1);
    permission(a, "flowboard:manage-routing", true);
    assertThat(alerts.deliver(tenant, failure)).isTrue();
    assertThat(notificationCount(a.getId())).isEqualTo(1);
    assertThat(notificationCount(b.getId())).isEqualTo(1);
  }

  @Test
  void historicalDeliveredRowCannotCompleteAnEmptyCurrentHolderSet() {
    var a = user("A", "flowboard:manage-routing");
    var b = user("B", "flowboard:manage-routing");
    seedNotificationTemplate();
    UUID failure = openFailure();
    doThrow(new IllegalStateException("temporary"))
        .when(notifications)
        .deliver(eq(tenant), eq(b.getId()), any(), eq(failure), any());
    assertThat(alerts.deliver(tenant, failure)).isFalse();
    permission(a, "flowboard:manage-routing", false);
    tx(() -> alerts.reconcile(tenant, failure));
    permission(b, "flowboard:manage-routing", false);
    tx(
        () -> {
          alerts.deliverOne(tenant, failure, alert(failure, b.getId()).id(), new int[] {-1});
          return null;
        });
    assertThat(tx(() -> alerts.complete(tenant, failure))).isFalse();
    assertThat(alert(failure, a.getId()).status()).isEqualTo("DELIVERED");
    assertThat(alert(failure, b.getId()).status()).isEqualTo("CANCELLED");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM event_publication WHERE serialized_event LIKE ? AND completion_date IS NULL",
                Integer.class,
                "%" + failure + "%"))
        .isGreaterThan(0);
    permission(b, "flowboard:manage-routing", true);
    doCallRealMethod()
        .when(notifications)
        .deliver(eq(tenant), eq(b.getId()), any(), eq(failure), any());
    assertThat(alerts.deliver(tenant, failure)).isTrue();
    assertThat(notificationCount(b.getId())).isEqualTo(1);
  }

  @Test
  void lateFailedRecordNeverOverwritesConcurrentDelivery() throws Exception {
    lateFailure(false);
  }

  @Test
  void lateFailedRecordNeverOverwritesConcurrentCancellation() throws Exception {
    lateFailure(true);
  }

  private void lateFailure(boolean cancel) throws Exception {
    var holder = user("Manager", "flowboard:manage-routing");
    seedNotificationTemplate();
    UUID failure = openFailure();
    tx(() -> alerts.reconcile(tenant, failure));
    var pending = alert(failure, holder.getId());
    int[] token = {-1};
    doAnswer(
            invocation -> {
              invocation.callRealMethod();
              throw new IllegalStateException("first attempt rolled back");
            })
        .when(notifications)
        .deliver(eq(tenant), eq(holder.getId()), any(), eq(failure), any());
    assertThatThrownBy(
            () ->
                tx(
                    () -> {
                      alerts.deliverOne(tenant, failure, pending.id(), token);
                      return null;
                    }))
        .isInstanceOf(IllegalStateException.class);
    assertThat(notificationCount(holder.getId())).isZero();
    doCallRealMethod()
        .when(notifications)
        .deliver(eq(tenant), eq(holder.getId()), any(), eq(failure), any());
    CountDownLatch delayed = new CountDownLatch(1), resume = new CountDownLatch(1);
    try (var executor = Executors.newSingleThreadExecutor()) {
      var recorder =
          executor.submit(
              () ->
                  inTenant(
                      () -> {
                        delayed.countDown();
                        await(resume);
                        return tx(
                            () -> {
                              alerts.recordFailure(
                                  tenant,
                                  failure,
                                  pending.id(),
                                  token[0],
                                  new IllegalStateException("late attempt"));
                              return true;
                            });
                      }));
      await(delayed);
      if (cancel) permission(holder, "flowboard:manage-routing", false);
      alerts.deliver(tenant, failure);
      resume.countDown();
      recorder.get(30, TimeUnit.SECONDS);
    } finally {
      resume.countDown();
    }
    assertThat(alert(failure, holder.getId()).status())
        .isEqualTo(cancel ? "CANCELLED" : "DELIVERED");
    assertThat(notificationCount(holder.getId())).isEqualTo(cancel ? 0 : 1);
  }

  @Test
  void resolutionBeforeReconcileCreatesNoAlert() throws Exception {
    user("Manager", "flowboard:manage-routing");
    seedNotificationTemplate();
    UUID failure = openFailure();
    CountDownLatch resolved = new CountDownLatch(1), release = new CountDownLatch(1);
    var readerPid = new java.util.concurrent.atomic.AtomicInteger();
    var executor = Executors.newFixedThreadPool(2);
    try {
      var resolution =
          executor.submit(
              () ->
                  inTenant(
                      () ->
                          tx(
                              () -> {
                                resolve(failure);
                                resolved.countDown();
                                await(release);
                                return true;
                              })));
      await(resolved, resolution);
      var reconcile =
          executor.submit(
              () ->
                  inTenant(
                      () ->
                          tx(
                              () -> {
                                readerPid.set(
                                    jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                                return alerts.reconcile(tenant, failure);
                              })));
      awaitBlocked(readerPid, reconcile);
      release.countDown();
      resolution.get(30, TimeUnit.SECONDS);
      assertThat(reconcile.get(30, TimeUnit.SECONDS)).isEmpty();
    } finally {
      release.countDown();
      executor.close();
    }
    assertThat(alerts.deliver(tenant, failure)).isTrue();
    assertThat(repository.alerts(tenant, failure)).isEmpty();
    assertThat(count("notification.notification_log")).isZero();
  }

  @Test
  void reconcileHoldingStatePreventsResolutionFromSlippingBeforeAlertInsert() throws Exception {
    user("Manager", "flowboard:manage-routing");
    seedNotificationTemplate();
    UUID failure = openFailure();
    CountDownLatch guarded = new CountDownLatch(1),
        resume = new CountDownLatch(1),
        resolving = new CountDownLatch(1);
    var writerPid = new java.util.concurrent.atomic.AtomicInteger();
    var executor = Executors.newFixedThreadPool(2);
    try {
      var reconcile =
          executor.submit(
              () ->
                  inTenant(
                      () ->
                          tx(
                              () -> {
                                repository.shareState(
                                    tenant, repository.failure(tenant, failure).taskId());
                                guarded.countDown();
                                await(resume);
                                return alerts.reconcile(tenant, failure).size();
                              })));
      await(guarded, reconcile);
      var resolution =
          executor.submit(
              () ->
                  inTenant(
                      () ->
                          tx(
                              () -> {
                                writerPid.set(
                                    jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                                resolving.countDown();
                                resolve(failure);
                                return true;
                              })));
      await(resolving, resolution);
      awaitBlocked(writerPid, resolution);
      resume.countDown();
      reconcile.get(30, TimeUnit.SECONDS);
      resolution.get(30, TimeUnit.SECONDS);
    } finally {
      resume.countDown();
      executor.close();
    }
    assertThat(repository.alerts(tenant, failure))
        .allSatisfy(
            a -> {
              assertThat(a.status()).isEqualTo("CANCELLED");
              assertThat(a.cancelReason()).isEqualTo("FAILURE_RESOLVED");
            });
    assertThat(alerts.deliver(tenant, failure)).isTrue();
    assertThat(count("notification.notification_log")).isZero();
  }

  private void resolve(UUID failure) {
    tx(
        () -> {
          var f = repository.failure(tenant, failure);
          repository.lockPool(tenant, f.poolKey(), false);
          repository.lockState(tenant, f.taskId(), f.poolKey());
          repository.resolve(tenant, failure);
          return null;
        });
  }

  private static final class RoutingEventListenerTarget {
    static void assertIncomplete(
        com.fabricmanagement.flowboard.routing.app.listener.RoutingEventListener proxy,
        RoutingFailureOpenedEvent event) {
      com.fabricmanagement.flowboard.routing.app.listener.RoutingEventListener target =
          org.springframework.test.util.AopTestUtils.getUltimateTargetObject(proxy);
      assertThatThrownBy(() -> target.onRoutingFailureOpened(event))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("remains owed");
    }
  }
}
