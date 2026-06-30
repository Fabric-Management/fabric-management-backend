package com.fabricmanagement.common.infrastructure.security;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.security.dto.PermissionResult;
import com.fabricmanagement.platform.user.domain.DataScope;
import com.fabricmanagement.platform.user.domain.SystemUser;
import com.fabricmanagement.platform.user.infra.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

@Component("scope")
@RequiredArgsConstructor
public class DataScopeGuard {

  private static final String DEPARTMENT_MEMBER_IDS_CACHE =
      DataScopeGuard.class.getName() + ".departmentMemberIds";

  private final PermissionEvaluator permissionEvaluator;
  private final AuthenticatedUserContextResolver contextResolver;
  private final UserRepository userRepository;

  public DataScope currentScope(String resource, String action) {
    Optional<AuthenticatedUserContext> context = currentContext();
    if (isSystemContext(context.orElse(null))) {
      return DataScope.GLOBAL;
    }

    AuthenticatedUserContext authenticated =
        context.orElseThrow(() -> forbidden(resource, "UNKNOWN"));
    PermissionResult result =
        permissionEvaluator.evaluate(
            authenticated.tenantId(),
            authenticated.roleCode(),
            authenticated.departmentCodes(),
            authenticated.userId());
    return result.scopeOf(resource, action);
  }

  public void assertCanAccess(String resource, String action, BaseEntity entity) {
    if (isSystemContext(currentContext().orElse(null))) {
      return;
    }
    if (entity == null) {
      throw forbidden(resource, "UNKNOWN");
    }

    DataScope scope = currentScope(resource, action);
    UUID currentUserId = currentUserId();
    if (scope == null || currentUserId == null) {
      throw forbidden(resource, String.valueOf(scope));
    }

    if (scope == DataScope.GLOBAL || scope == DataScope.ORGANIZATION) {
      return;
    }

    UUID createdBy = entity.getCreatedBy();
    if (scope == DataScope.OWN && currentUserId.equals(createdBy)) {
      return;
    }

    if (scope == DataScope.DEPARTMENT && departmentMemberIds().contains(createdBy)) {
      return;
    }

    throw forbidden(resource, scope.name());
  }

  public <T> Specification<T> scopeFilter(String resource, String action) {
    if (isSystemContext(currentContext().orElse(null))) {
      return (root, query, cb) -> cb.conjunction();
    }

    DataScope scope = currentScope(resource, action);
    UUID currentUserId = currentUserId();
    if (scope == null || currentUserId == null) {
      return (root, query, cb) -> cb.disjunction();
    }

    return switch (scope) {
      case GLOBAL, ORGANIZATION -> (root, query, cb) -> cb.conjunction();
      case OWN -> (root, query, cb) -> cb.equal(root.get("createdBy"), currentUserId);
      case DEPARTMENT ->
          (root, query, cb) -> {
            Set<UUID> memberIds = departmentMemberIds();
            if (memberIds.isEmpty()) {
              return cb.disjunction();
            }
            Predicate predicate = root.get("createdBy").in(memberIds);
            return predicate;
          };
    };
  }

  private Optional<AuthenticatedUserContext> currentContext() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    return contextResolver.resolve(authentication);
  }

  private UUID currentUserId() {
    return currentContext()
        .map(AuthenticatedUserContext::userId)
        .orElse(TenantContext.getCurrentUserId());
  }

  private boolean isSystemContext(AuthenticatedUserContext context) {
    UUID contextUserId = context != null ? context.userId() : null;
    UUID tenantContextUserId = TenantContext.getCurrentUserId();
    // Missing/anonymous authentication is not a bypass. Only explicit system execution may skip
    // row-scope checks.
    return SystemUser.ID.equals(contextUserId)
        || SystemUser.ID.equals(tenantContextUserId)
        || TenantContext.isSystemTenant();
  }

  @SuppressWarnings("unchecked")
  private Set<UUID> departmentMemberIds() {
    AuthenticatedUserContext context = currentContext().orElse(null);
    UUID tenantId = context != null ? context.tenantId() : TenantContext.getCurrentTenantIdOrNull();
    UUID userId = currentUserId();
    List<String> departmentCodes =
        context != null && context.departmentCodes() != null
            ? context.departmentCodes()
            : List.of();

    Set<String> normalizedCodes = normalizeDepartmentCodes(departmentCodes);
    if (tenantId == null || normalizedCodes.isEmpty()) {
      return userId == null ? Set.of() : Set.of(userId);
    }

    String cacheKey = cacheKey(tenantId, normalizedCodes);
    RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
    if (requestAttributes != null) {
      Object cached = requestAttributes.getAttribute(cacheKey, RequestAttributes.SCOPE_REQUEST);
      if (cached instanceof Set<?> cachedSet) {
        return (Set<UUID>) cachedSet;
      }
    }

    Set<UUID> memberIds =
        new HashSet<>(userRepository.findActiveUserIdsByDepartmentCodes(tenantId, normalizedCodes));
    if (userId != null) {
      memberIds.add(userId);
    }
    Set<UUID> immutableMemberIds = Set.copyOf(memberIds);

    if (requestAttributes != null) {
      requestAttributes.setAttribute(cacheKey, immutableMemberIds, RequestAttributes.SCOPE_REQUEST);
    }
    return immutableMemberIds;
  }

  private Set<String> normalizeDepartmentCodes(Collection<String> departmentCodes) {
    return departmentCodes.stream()
        .filter(code -> code != null && !code.isBlank())
        .map(code -> code.toUpperCase(java.util.Locale.ROOT))
        .collect(Collectors.toUnmodifiableSet());
  }

  private String cacheKey(UUID tenantId, Set<String> departmentCodes) {
    return DEPARTMENT_MEMBER_IDS_CACHE
        + ":"
        + tenantId
        + ":"
        + departmentCodes.stream().sorted().collect(Collectors.joining(","));
  }

  private AccessDeniedException forbidden(String resource, String scope) {
    return new AccessDeniedException(
        "You do not have access to this " + resource + " record (scope: " + scope + ").");
  }
}
