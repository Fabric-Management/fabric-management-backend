package com.fabricmanagement.sales.salesorder.infra.repository;

import com.fabricmanagement.sales.salesorder.domain.paymentplan.OrderPaymentPlan;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderPaymentPlanRepository extends JpaRepository<OrderPaymentPlan, UUID> {
  /**
   * The version stamp of every plan that was ever agreed, paired with the version of its order. A
   * draft-only plan owns no obligations; a cancelled plan keeps its agreed pointer, so it is still
   * found and its obligations get closed. Columns only: no plan, revision or order entity is
   * loaded, so listing every agreed order of a tenant stays cheap.
   */
  @org.springframework.data.jpa.repository.Query(
      "select p.salesOrderId as orderId, o.version as orderVersion, p.version as planVersion,"
          + " p.activeAgreedRevisionId as revisionId, p.cancelled as cancelled"
          + " from OrderPaymentPlan p, SalesOrder o"
          + " where o.tenantId = p.tenantId and o.id = p.salesOrderId"
          + " and p.tenantId = :tenant and p.salesOrderId > :after"
          + " and p.activeAgreedRevisionId is not null order by p.salesOrderId")
  java.util.List<AgreedStamp> agreedStampsAfter(
      @org.springframework.data.repository.query.Param("tenant") UUID tenant,
      @org.springframework.data.repository.query.Param("after") UUID after,
      org.springframework.data.domain.Pageable page);

  /** Projection of {@link #agreedStampsAfter}. */
  interface AgreedStamp {
    UUID getOrderId();

    Long getOrderVersion();

    Long getPlanVersion();

    UUID getRevisionId();

    Boolean getCancelled();
  }

  Optional<OrderPaymentPlan> findByTenantIdAndSalesOrderId(UUID tenant, UUID order);
}
