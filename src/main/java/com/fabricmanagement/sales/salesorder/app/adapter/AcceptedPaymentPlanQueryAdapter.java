package com.fabricmanagement.sales.salesorder.app.adapter;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.sales.salesorder.app.PaymentPlanStore;
import com.fabricmanagement.sales.salesorder.app.port.AcceptedPaymentPlanQueryPort;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AcceptedPaymentPlanQueryAdapter implements AcceptedPaymentPlanQueryPort {
  private final PaymentPlanStore store;
  private final SalesOrderRepository orders;

  @Override
  @Transactional(readOnly = true)
  public Optional<AcceptedPlan> find(UUID tenantId, UUID orderId) {
    // Asking about another tenant is a bug; "no accepted plan" would read as an answer.
    if (!TenantContext.requireTenantId().equals(tenantId))
      throw new IllegalStateException(
          "The accepted payment plan was requested outside the bound tenant");
    return orders
        .findByTenantIdAndId(tenantId, orderId)
        .flatMap(
            order ->
                store
                    .plan(orderId)
                    .flatMap(
                        root -> {
                          if (root.getActiveAgreedRevisionId() == null) return Optional.empty();
                          var revision = store.revision(root, root.getActiveAgreedRevisionId());
                          if (revision.getActivatedAt() == null) return Optional.empty();
                          return Optional.of(
                              new AcceptedPlan(
                                  orderId,
                                  order.getTradingPartnerId(),
                                  root.getId(),
                                  root.getVersion(),
                                  store.content(revision),
                                  revision.getAgreedApprovalId(),
                                  revision.getAgreedAt(),
                                  revision.getActivatedAt(),
                                  revision.getActivationDate(),
                                  root.isCancelled()));
                        }));
  }
}
