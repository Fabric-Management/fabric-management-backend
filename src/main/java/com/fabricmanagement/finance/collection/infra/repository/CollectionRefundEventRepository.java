package com.fabricmanagement.finance.collection.infra.repository;

import com.fabricmanagement.finance.collection.domain.CollectionRefundEvent;
import com.fabricmanagement.finance.collection.domain.CollectionTypes.RefundAction;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollectionRefundEventRepository
    extends JpaRepository<CollectionRefundEvent, UUID> {
  List<CollectionRefundEvent> findByTenantIdAndRefundIdInOrderByRefundIdAscStepAsc(
      UUID tenant, Collection<UUID> refunds);

  List<CollectionRefundEvent> findByTenantIdAndPaymentIdAndAction(
      UUID tenant, UUID payment, RefundAction action);
}
