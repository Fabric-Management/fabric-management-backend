package com.fabricmanagement.production.execution.workorder.infra.repository;

import com.fabricmanagement.production.execution.workorder.domain.WorkOrder;
import com.fabricmanagement.production.execution.workorder.domain.WorkOrderStatus;
import com.fabricmanagement.production.execution.workorder.dto.WorkOrderFilterRequest;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * Static factory for {@link WorkOrder} filter predicates.
 *
 * <p>Each method returns an independent {@link Specification} — combined with {@link
 * Specification#and} for multi-criteria queries. New filters can be added here without touching any
 * other class.
 */
public final class WorkOrderSpecification {

  private WorkOrderSpecification() {}

  /** Always-on: tenant isolation + soft delete guard. */
  public static Specification<WorkOrder> belongsToTenant(UUID tenantId) {
    return (root, query, cb) ->
        cb.and(cb.equal(root.get("tenantId"), tenantId), cb.isTrue(root.get("isActive")));
  }

  public static Specification<WorkOrder> hasStatus(WorkOrderStatus status) {
    return (root, query, cb) -> cb.equal(root.get("status"), status);
  }

  public static Specification<WorkOrder> hasTradingPartner(UUID tradingPartnerId) {
    return (root, query, cb) -> cb.equal(root.get("tradingPartnerId"), tradingPartnerId);
  }

  public static Specification<WorkOrder> hasSalesOrder(UUID salesOrderId) {
    return (root, query, cb) -> cb.equal(root.get("salesOrderId"), salesOrderId);
  }

  public static Specification<WorkOrder> hasRecipe(UUID recipeId) {
    return (root, query, cb) -> cb.equal(root.get("recipeId"), recipeId);
  }

  /**
   * Case-insensitive partial match on workOrderNumber or productCode.
   *
   * <p>LIKE wildcards ({@code %}, {@code _}) in user input are escaped to prevent unintended
   * pattern matching.
   */
  public static Specification<WorkOrder> searchText(String text) {
    String sanitized =
        text.toLowerCase()
            .replace("\\", "\\\\") // escape backslash first
            .replace("%", "\\%")
            .replace("_", "\\_");
    String pattern = "%" + sanitized + "%";
    return (root, query, cb) ->
        cb.or(
            cb.like(cb.lower(root.get("workOrderNumber")), pattern, '\\'),
            cb.like(cb.lower(cb.coalesce(root.get("productCode"), cb.literal(""))), pattern, '\\'));
  }

  public static Specification<WorkOrder> deadlineFrom(Instant from) {
    return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("deadline"), from);
  }

  public static Specification<WorkOrder> deadlineTo(Instant to) {
    return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("deadline"), to);
  }

  public static Specification<WorkOrder> createdFrom(Instant from) {
    return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), from);
  }

  public static Specification<WorkOrder> createdTo(Instant to) {
    return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), to);
  }

  /**
   * Builds a combined Specification from a filter DTO. Only non-null fields are applied. Tenant
   * guard is always included.
   */
  public static Specification<WorkOrder> build(UUID tenantId, WorkOrderFilterRequest filter) {
    Specification<WorkOrder> spec = Specification.where(belongsToTenant(tenantId));

    if (filter.status() != null) spec = spec.and(hasStatus(filter.status()));
    if (filter.tradingPartnerId() != null)
      spec = spec.and(hasTradingPartner(filter.tradingPartnerId()));
    if (filter.salesOrderId() != null) spec = spec.and(hasSalesOrder(filter.salesOrderId()));
    if (filter.recipeId() != null) spec = spec.and(hasRecipe(filter.recipeId()));
    if (filter.searchText() != null && !filter.searchText().isBlank()) {
      spec = spec.and(searchText(filter.searchText()));
    }
    if (filter.deadlineFrom() != null) spec = spec.and(deadlineFrom(filter.deadlineFrom()));
    if (filter.deadlineTo() != null) spec = spec.and(deadlineTo(filter.deadlineTo()));
    if (filter.createdFrom() != null) spec = spec.and(createdFrom(filter.createdFrom()));
    if (filter.createdTo() != null) spec = spec.and(createdTo(filter.createdTo()));

    return spec;
  }
}
