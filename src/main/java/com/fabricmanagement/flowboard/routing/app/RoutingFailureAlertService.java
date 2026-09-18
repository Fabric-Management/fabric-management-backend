package com.fabricmanagement.flowboard.routing.app;

import com.fabricmanagement.common.infrastructure.security.PermissionKey;
import com.fabricmanagement.flowboard.routing.domain.RoutingRecords.*;
import com.fabricmanagement.flowboard.routing.domain.port.out.*;
import com.fabricmanagement.flowboard.routing.infra.repository.RoutingRepository;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;

/** The orchestration never owns a transaction. Every durable step commits independently. */
@Service
@Slf4j
public class RoutingFailureAlertService {
  private final RoutingRepository repository;
  private final EffectivePermissionUserQueryPort holders;
  private final RoutingUserQueryPort users;
  private final RoutingNotificationPort notifications;
  private final TransactionTemplate transactions;

  public RoutingFailureAlertService(
      RoutingRepository repository,
      EffectivePermissionUserQueryPort holders,
      RoutingUserQueryPort users,
      RoutingNotificationPort notifications,
      PlatformTransactionManager manager) {
    this.repository = repository;
    this.holders = holders;
    this.users = users;
    this.notifications = notifications;
    transactions = new TransactionTemplate(manager);
    transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    transactions.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public boolean deliver(UUID tenant, UUID failureId) {
    List<Alert> nominees = transactions.execute(status -> reconcile(tenant, failureId));
    for (Alert nominated : Objects.requireNonNull(nominees)) {
      int[] token = {-1};
      try {
        transactions.executeWithoutResult(
            status -> deliverOne(tenant, failureId, nominated.id(), token));
      } catch (RuntimeException error) {
        if (token[0] >= 0) {
          transactions.executeWithoutResult(
              status -> recordFailure(tenant, failureId, nominated.id(), token[0], error));
        } else {
          log.warn(
              "Routing alert failed before obtaining attempt token: alert={}",
              nominated.id(),
              error);
        }
      }
    }
    return Boolean.TRUE.equals(transactions.execute(status -> complete(tenant, failureId)));
  }

  private Failure guardedFailure(UUID tenant, UUID id) {
    // Identity lookup only; no openness decision is made before the state lock.
    Failure identity = repository.failure(tenant, id);
    repository.shareState(tenant, identity.taskId());
    return repository.failure(tenant, id);
  }

  List<Alert> reconcile(UUID tenant, UUID failureId) {
    Failure failure = guardedFailure(tenant, failureId);
    if (failure.resolvedAt() != null) {
      repository.cancelResolved(tenant, failureId);
      return List.of();
    }
    Set<UUID> current = holders.findUsersWithAction(tenant, PermissionKey.FLOWBOARD_MANAGE_ROUTING);
    // Lock the existing rows in one order before either cancelling or reactivating them.
    // Two reconcilers may have observed different holder sets; locking only each one's
    // cancellations first would invert their alert-lock order.
    var existing =
        repository.alerts(tenant, failureId).stream()
            .map(alert -> repository.lockAlert(tenant, alert.id()))
            .toList();
    existing.forEach(
        alert -> {
          if (!current.contains(alert.recipientId()))
            repository.cancelRecipient(tenant, alert.id());
        });
    current.stream().sorted().forEach(user -> repository.nominate(tenant, failureId, user));
    return repository.alerts(tenant, failureId).stream()
        .filter(RoutingFailureAlertService::pending)
        .toList();
  }

  void deliverOne(UUID tenant, UUID failureId, UUID alertId, int[] token) {
    Failure failure = guardedFailure(tenant, failureId);
    if (failure.resolvedAt() != null) {
      repository.cancelResolved(tenant, failureId);
      return;
    }
    Alert alert = repository.lockAlert(tenant, alertId);
    if (!pending(alert)) return;
    token[0] = alert.attempts();
    var user = users.findFresh(tenant, alert.recipientId());
    var permission = PermissionKey.FLOWBOARD_MANAGE_ROUTING;
    if (!user.active()
        || !tenant.equals(user.tenantId())
        || !user.permissions().can(permission.resource(), permission.action())) {
      repository.cancelRecipient(tenant, alertId);
      return;
    }
    notifications.deliver(tenant, alert.recipientId(), alert.id(), failureId, failure.taskId());
    repository.delivered(tenant, alertId);
  }

  void recordFailure(UUID tenant, UUID failureId, UUID alertId, int token, RuntimeException error) {
    Failure failure = guardedFailure(tenant, failureId);
    if (failure.resolvedAt() != null) {
      repository.cancelResolved(tenant, failureId);
      return;
    }
    repository.lockAlert(tenant, alertId);
    String message =
        error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    if (repository.failed(
            tenant, alertId, token, message.substring(0, Math.min(500, message.length())))
        == 0) log.warn("Late routing alert failure ignored: alert={}", alertId, error);
  }

  boolean complete(UUID tenant, UUID failureId) {
    if (guardedFailure(tenant, failureId).resolvedAt() != null) return true;
    Set<UUID> now = holders.findUsersWithAction(tenant, PermissionKey.FLOWBOARD_MANAGE_ROUTING);
    Set<UUID> delivered = new HashSet<>();
    repository.alerts(tenant, failureId).stream()
        .filter(a -> "DELIVERED".equals(a.status()))
        .forEach(a -> delivered.add(a.recipientId()));
    return !now.isEmpty() && delivered.containsAll(now);
  }

  private static boolean pending(Alert alert) {
    return "PENDING".equals(alert.status()) || "FAILED".equals(alert.status());
  }
}
