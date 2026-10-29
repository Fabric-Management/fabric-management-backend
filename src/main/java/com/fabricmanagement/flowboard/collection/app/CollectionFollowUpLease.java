package com.fabricmanagement.flowboard.collection.app;

import com.fabricmanagement.common.infrastructure.scheduling.TenantRunLease;
import javax.sql.DataSource;
import org.springframework.stereotype.Component;

/** Lets one instance at a time scan a tenant's follow-ups; see {@link TenantRunLease}. */
@Component
public class CollectionFollowUpLease extends TenantRunLease {
  public CollectionFollowUpLease(DataSource dataSource) {
    super(dataSource, "collection-follow-up");
  }
}
