package com.fabricmanagement.finance.collection.app;

import com.fabricmanagement.common.infrastructure.scheduling.TenantRunExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** The threads collection reconciliation runs on; see {@link TenantRunExecutor}. */
@Component
public class CollectionReconciliationExecutor extends TenantRunExecutor {
  public CollectionReconciliationExecutor(
      @Value("${finance.collections.parallelism:2}") int parallelism) {
    super("collection-reconcile", parallelism);
  }
}
