package com.fabricmanagement.sales.salesorder.app.adapter;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.finance.collection.app.port.CollectionSourcePort;
import com.fabricmanagement.finance.collection.domain.CollectionTypes.Kind;
import com.fabricmanagement.finance.collection.domain.CollectionTypes.Method;
import com.fabricmanagement.sales.salesorder.app.PaymentPlanStore;
import com.fabricmanagement.sales.salesorder.app.port.AcceptedPaymentPlanQueryPort;
import com.fabricmanagement.sales.salesorder.domain.paymentplan.PaymentPlanTypes.DateTrigger;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderPaymentPlanRepository;
import com.fabricmanagement.sales.salesorder.infra.repository.SalesOrderRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockTimeoutException;
import jakarta.persistence.PessimisticLockException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class CollectionSourceAdapter implements CollectionSourcePort {
  /**
   * The longest finance waits for a writer of the order. Finance already holds the tenant-wide
   * finance lock when it asks, so this bounds how long one order edit can stall finance.
   */
  static final String LOCK_WAIT = "5s";

  static final String EXISTS_SQL =
      "select 1 from sales_ord.sales_order where tenant_id = :tenant and id = :id";
  static final String HOLD_NOW_SQL = EXISTS_SQL + " for share skip locked";

  private final AcceptedPaymentPlanQueryPort accepted;
  private final OrderPaymentPlanRepository plans;
  private final SalesOrderRepository orders;
  private final PaymentPlanStore store;
  private final EntityManager entityManager;

  @Override
  @Transactional
  public Optional<Agreement> find(UUID tenant, UUID orderId) {
    requireBoundTenant(tenant);
    if (!lockShared(tenant, orderId)) return Optional.empty();
    return accepted
        .find(tenant, orderId)
        .map(
            source -> {
              var order = orders.findByTenantIdAndId(tenant, orderId).orElseThrow();
              var root = store.plan(orderId).orElseThrow();
              var revision = store.revision(root, root.getActiveAgreedRevisionId());
              return new Agreement(
                  orderId,
                  source.partnerId(),
                  source.planId(),
                  source.content().revisionId(),
                  source.planVersion(),
                  source.approvalId(),
                  source.agreedAt(),
                  source.activatedAt(),
                  source.cancelled(),
                  store.stale(order, revision),
                  source.content().currencyGroups().stream()
                      .flatMap(
                          group ->
                              group.lines().stream()
                                  .map(
                                      line ->
                                          new Expectation(
                                              line.obligationId(),
                                              group.currency(),
                                              line.amount(),
                                              Kind.valueOf(line.kind().name()),
                                              line.method() == null
                                                  ? null
                                                  : Method.valueOf(line.method().name()),
                                              line.dateTrigger()
                                                      == DateTrigger.ON_ORDER_CONFIRMATION
                                                  ? source.confirmationDate()
                                                  : line.expectedDate(),
                                              line.dateTrigger()
                                                      == DateTrigger.ON_ORDER_CONFIRMATION
                                                  && source.confirmationDate() == null,
                                              line.chequePaymentDate())))
                      .toList(),
                  revision.getCancelledAt(),
                  order.getVersion());
            });
  }

  /**
   * Share-locks the order for the rest of the transaction, waiting for a writer at most {@link
   * #LOCK_WAIT}. A timed-out lock aborts the transaction, so the caller's work is rolled back and
   * nothing half-read is used. The caller's own timeout is put back once the lock is held.
   */
  private boolean lockShared(UUID tenant, UUID orderId) {
    String previous = String.valueOf(lockTimeout().getSingleResult());
    setLockTimeout(LOCK_WAIT);
    boolean found;
    try {
      found = orders.lockSharedByTenantIdAndId(tenant, orderId).isPresent();
    } catch (PessimisticLockingFailureException
        | LockTimeoutException
        | PessimisticLockException busy) {
      throw new SourceBusyException(orderId);
    }
    setLockTimeout(previous);
    return found;
  }

  /**
   * {@code FOR SHARE SKIP LOCKED} takes the lock only if nobody holds a conflicting one, and skips
   * the row otherwise: no wait, no error, and the transaction is not aborted. A row that is not
   * there at all is told apart from a locked one by a plain read, which never blocks.
   */
  @Override
  @Transactional
  public boolean tryHold(UUID tenant, UUID orderId) {
    requireBoundTenant(tenant);
    if (!returnsRow(EXISTS_SQL, tenant, orderId)) return true;
    return returnsRow(HOLD_NOW_SQL, tenant, orderId);
  }

  // Asking about another tenant is a bug. "No such order" would be read as a cancelled agreement
  // and "nothing to list" as a clean run, so the mistake would look like an answer.
  private static void requireBoundTenant(UUID tenant) {
    if (!TenantContext.requireTenantId().equals(tenant))
      throw new IllegalStateException(
          "The collection source was requested outside the bound tenant");
  }

  private boolean returnsRow(String sql, UUID tenant, UUID orderId) {
    return !entityManager
        .createNativeQuery(sql)
        .setParameter("tenant", tenant)
        .setParameter("id", orderId)
        .getResultList()
        .isEmpty();
  }

  private jakarta.persistence.Query lockTimeout() {
    return entityManager.createNativeQuery("select current_setting('lock_timeout')");
  }

  private void setLockTimeout(String value) {
    entityManager
        .createNativeQuery("select set_config('lock_timeout', :value, true)")
        .setParameter("value", value)
        .getSingleResult();
  }

  @Override
  @Transactional(readOnly = true)
  public List<SourceStamp> stamps(UUID tenant, UUID after, int limit) {
    requireBoundTenant(tenant);
    return plans.agreedStampsAfter(tenant, after, PageRequest.of(0, Math.min(limit, 200))).stream()
        .map(
            value ->
                new SourceStamp(
                    value.getOrderId(),
                    value.getOrderVersion(),
                    value.getPlanVersion(),
                    value.getRevisionId(),
                    value.getCancelled()))
        .toList();
  }
}
