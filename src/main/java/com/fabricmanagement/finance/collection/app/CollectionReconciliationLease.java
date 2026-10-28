package com.fabricmanagement.finance.collection.app;

import com.fabricmanagement.common.infrastructure.scheduling.TenantRunLease;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;

/**
 * Lets one application instance at a time reconcile a tenant; see {@link TenantRunLease}. Every
 * read of an order takes the finance tenant lock, so two instances could not corrupt anything: the
 * lease only saves the second one's work.
 */
@Component
public class CollectionReconciliationLease extends TenantRunLease {
  public CollectionReconciliationLease(DataSource dataSource) {
    super(dataSource, "collection-reconcile");
  }
}
