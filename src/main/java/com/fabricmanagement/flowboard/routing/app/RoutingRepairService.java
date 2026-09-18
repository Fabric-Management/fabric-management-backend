package com.fabricmanagement.flowboard.routing.app;

import com.fabricmanagement.flowboard.routing.domain.RoutingPoolKey;
import com.fabricmanagement.flowboard.routing.domain.RoutingRecords.Repair;
import com.fabricmanagement.flowboard.routing.domain.port.in.GovernedTaskRoutingPort;
import com.fabricmanagement.flowboard.routing.infra.repository.RoutingRepository;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class RoutingRepairService {
  private final RoutingRepository repository;
  private final GovernedTaskRoutingPort routing;
  private final RoutingFailureAlertService alerts;
  private final TransactionTemplate transactions;

  public RoutingRepairService(
      RoutingRepository repository,
      GovernedTaskRoutingPort routing,
      RoutingFailureAlertService alerts,
      PlatformTransactionManager manager) {
    this.repository = repository;
    this.routing = routing;
    this.alerts = alerts;
    transactions = new TransactionTemplate(manager);
    transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    transactions.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
  }

  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public Repair repair(UUID tenant, RoutingPoolKey key, boolean changedOnly) {
    var taskIds = transactions.execute(status -> repository.repairTasks(tenant, key, changedOnly));
    int evaluated = 0, changed = 0, opened = 0, resolved = 0;
    for (UUID task : Objects.requireNonNull(taskIds)) {
      var result =
          Objects.requireNonNull(
              transactions.execute(status -> routing.evaluate(tenant, key, task)));
      evaluated++;
      if (result.changed()) changed++;
      opened += result.opened();
      resolved += result.resolved();
    }
    var failures = transactions.execute(status -> repository.failures(tenant, key, true));
    boolean complete = true;
    for (var failure : Objects.requireNonNull(failures)) {
      if (!alerts.deliver(tenant, failure.id())) complete = false;
    }
    return new Repair(evaluated, changed, opened, resolved, complete);
  }
}
