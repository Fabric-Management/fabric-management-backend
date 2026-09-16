package com.fabricmanagement.sales.salesorder.app;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.sales.salesorder.infra.repository.OrderCoverEvidenceStreamRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Authenticated internal operator entry point; each snapshot uses its own bounded transaction. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderCoverEvidenceRebuildService {
  private final OrderCoverEvidenceStreamRepository streams;
  private final OrderCoverEvidenceService evidence;

  @PreAuthorize(
      "@auth.can(authentication, 'flowboard', 'write') and @auth.can(authentication, 'sales',"
          + " 'write') and @auth.hasScope(authentication, 'sales', 'write', 'ORGANIZATION')")
  public int rebuildOrder(UUID orderId) {
    var scopes =
        streams.findByTenantIdAndSalesOrderIdOrderByCaseId(
            TenantContext.requireTenantId(), orderId);
    scopes.forEach(scope -> evidence.rebuild(scope.getSalesOrderId(), scope.getCaseId()));
    return scopes.size();
  }

  /** Rebuild only a bounded page; the authenticated operator advances page after success. */
  @PreAuthorize(
      "@auth.can(authentication, 'flowboard', 'write') and @auth.can(authentication, 'sales',"
          + " 'write') and @auth.hasScope(authentication, 'sales', 'write', 'ORGANIZATION')")
  public RebuildPage rebuildTenantPage(int page) {
    var scopes =
        streams.findByTenantId(
            TenantContext.requireTenantId(),
            PageRequest.of(page, 50, Sort.by("createdAt").ascending().and(Sort.by("id"))));
    scopes.forEach(scope -> evidence.rebuild(scope.getSalesOrderId(), scope.getCaseId()));
    return new RebuildPage(scopes.getNumberOfElements(), scopes.hasNext() ? page + 1 : null);
  }

  public record RebuildPage(int rebuilt, Integer nextPage) {}
}
