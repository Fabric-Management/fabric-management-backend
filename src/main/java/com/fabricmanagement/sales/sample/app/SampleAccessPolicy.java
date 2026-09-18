package com.fabricmanagement.sales.sample.app;

import com.fabricmanagement.sales.common.app.SalesAccessScopeResolver;
import com.fabricmanagement.sales.common.app.SalesAccessScopeResolver.AccessScope;
import com.fabricmanagement.sales.common.app.SalesAccessScopeResolver.PermissionFreshness;
import com.fabricmanagement.sales.sample.domain.SampleRequest;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SampleAccessPolicy {

  private final SalesAccessScopeResolver scopeResolver;

  public boolean canRead(UUID tenantId, UUID userId, SampleRequest request) {
    return canAccess(tenantId, userId, request, "read");
  }

  public boolean canWrite(UUID tenantId, UUID userId, SampleRequest request) {
    return canAccess(tenantId, userId, request, "write");
  }

  public Specification<SampleRequest> readRestriction(UUID tenantId, UUID userId) {
    AccessScope accessScope =
        scopeResolver.resolve(tenantId, userId, "read", PermissionFreshness.CACHED);
    return (root, query, criteriaBuilder) -> {
      var tenantPredicate = criteriaBuilder.equal(root.get("tenantId"), tenantId);
      if (accessScope.scope() == null) {
        return criteriaBuilder.and(tenantPredicate, criteriaBuilder.disjunction());
      }
      var scopePredicate =
          switch (accessScope.scope()) {
            case GLOBAL, ORGANIZATION -> criteriaBuilder.conjunction();
            case OWN, DEPARTMENT ->
                accessScope.permittedPrincipalIds().isEmpty()
                    ? criteriaBuilder.disjunction()
                    : root.get("createdBy").in(accessScope.permittedPrincipalIds());
          };
      return criteriaBuilder.and(tenantPredicate, scopePredicate);
    };
  }

  private boolean canAccess(UUID tenantId, UUID userId, SampleRequest request, String action) {
    if (tenantId == null || userId == null || request == null) {
      return false;
    }
    AccessScope scope = scopeResolver.resolve(tenantId, userId, action, PermissionFreshness.CACHED);
    Set<UUID> principals =
        request.getCreatedBy() == null ? Set.of() : Set.of(request.getCreatedBy());
    return scope.permits(request.getTenantId(), tenantId, principals);
  }
}
