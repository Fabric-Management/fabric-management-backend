package com.fabricmanagement.flowboard.collection.app;

import com.fabricmanagement.common.infrastructure.scheduling.TenantRunExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** The threads the collection follow-up scan runs on; see {@link TenantRunExecutor}. */
@Component
public class CollectionFollowUpExecutor extends TenantRunExecutor {
  public CollectionFollowUpExecutor(
      @Value("${flowboard.collections.parallelism:2}") int parallelism) {
    super("collection-follow-up", parallelism);
  }
}
