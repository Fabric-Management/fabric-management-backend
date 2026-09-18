package com.fabricmanagement.sales.quote.app;

import com.fabricmanagement.sales.common.app.SalesAccessScopeResolver;
import com.fabricmanagement.sales.common.app.SalesAccessScopeResolver.AccessScope;
import com.fabricmanagement.sales.common.app.SalesAccessScopeResolver.PermissionFreshness;
import com.fabricmanagement.sales.quote.domain.Quote;
import jakarta.persistence.criteria.Predicate;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class QuoteAccessPolicy {

  private final SalesAccessScopeResolver scopeResolver;

  public boolean canRead(UUID tenantId, UUID userId, Quote quote) {
    return canAccess(tenantId, userId, quote, "read");
  }

  public boolean canWrite(UUID tenantId, UUID userId, Quote quote) {
    return canAccess(tenantId, userId, quote, "write");
  }

  public Specification<Quote> readRestriction(UUID tenantId, UUID userId) {
    AccessScope accessScope =
        scopeResolver.resolve(tenantId, userId, "read", PermissionFreshness.CACHED);
    return (root, query, criteriaBuilder) -> {
      Predicate tenantPredicate = criteriaBuilder.equal(root.get("tenantId"), tenantId);
      if (accessScope.scope() == null) {
        return criteriaBuilder.and(tenantPredicate, criteriaBuilder.disjunction());
      }
      Predicate scopePredicate =
          switch (accessScope.scope()) {
            case GLOBAL, ORGANIZATION -> criteriaBuilder.conjunction();
            case OWN, DEPARTMENT ->
                accessScope.permittedPrincipalIds().isEmpty()
                    ? criteriaBuilder.disjunction()
                    : criteriaBuilder.or(
                        root.get("createdBy").in(accessScope.permittedPrincipalIds()),
                        root.get("assignedToId").in(accessScope.permittedPrincipalIds()));
          };
      return criteriaBuilder.and(tenantPredicate, scopePredicate);
    };
  }

  public Set<UUID> readableQuoteIds(UUID tenantId, UUID userId, Collection<Quote> quotes) {
    AccessScope scope = scopeResolver.resolve(tenantId, userId, "read", PermissionFreshness.CACHED);
    if (quotes == null || quotes.isEmpty()) {
      return Set.of();
    }
    return quotes.stream()
        .filter(quote -> scope.permits(quote.getTenantId(), tenantId, principals(quote)))
        .map(Quote::getId)
        .filter(java.util.Objects::nonNull)
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }

  private boolean canAccess(UUID tenantId, UUID userId, Quote quote, String action) {
    if (tenantId == null || userId == null || quote == null) {
      return false;
    }
    AccessScope scope = scopeResolver.resolve(tenantId, userId, action, PermissionFreshness.CACHED);
    return scope.permits(quote.getTenantId(), tenantId, principals(quote));
  }

  private Set<UUID> principals(Quote quote) {
    Set<UUID> principals = new LinkedHashSet<>();
    if (quote.getCreatedBy() != null) {
      principals.add(quote.getCreatedBy());
    }
    if (quote.getAssignedToId() != null) {
      principals.add(quote.getAssignedToId());
    }
    return Set.copyOf(principals);
  }
}
