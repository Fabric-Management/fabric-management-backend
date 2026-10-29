package com.fabricmanagement.platform.subscription.app.adapter;

import com.fabricmanagement.common.infrastructure.persistence.TenantContext;
import com.fabricmanagement.common.infrastructure.tenant.PackageAccessPort;
import com.fabricmanagement.common.infrastructure.tenant.TenantAccessPort;
import com.fabricmanagement.platform.subscription.domain.OSDefinition;
import com.fabricmanagement.platform.subscription.domain.Subscription;
import com.fabricmanagement.platform.subscription.infra.repository.OSDefinitionRepository;
import com.fabricmanagement.platform.subscription.infra.repository.SubscriptionRepository;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Decides which permission resources a tenant's enabled packages open.
 *
 * <p>The system catalogue is the single source of truth: a package's {@code included_modules} are
 * the permission resource names it owns. A resource is open when at least one package that owns it
 * is currently enabled for the tenant (non-paid evaluation access included, expired or deselected
 * rows excluded). Rules, each deliberate:
 *
 * <ul>
 *   <li>A tenant with no subscription row at all is not governed by packages. Sign-up always
 *       creates FabricOS, so every real tenant is governed from its first minute; only seeded,
 *       system and test tenants that never signed up keep role-only access.
 *   <li>A resource no package claims stays open, so a new resource is never locked by omission.
 *   <li>A tenant that is no longer writable (an expired real trial, for example) keeps read access
 *       to its data; the tenant write guard blocks changes. Packages gate live use, not retention.
 *   <li>A package definition keeps owning its resources after it is deactivated or soft-deleted.
 *       Ownership is read from the whole system catalogue; only a tenant's own subscription decides
 *       whether the package is enabled. Taking a package off the offer therefore never opens what
 *       it guarded, and tenants that already hold it keep it.
 * </ul>
 *
 * <p>The decision reads current database state on purpose: a revoked grant or a passed deadline
 * must take effect on the next request, never after a cache window.
 */
@Component
@RequiredArgsConstructor
public class PackageAccessAdapter implements PackageAccessPort {

  private final OSDefinitionRepository definitions;
  private final SubscriptionRepository subscriptions;
  private final TenantAccessPort tenantAccess;

  private record Access(boolean governed, Set<String> enabled, List<OSDefinition> catalogue) {
    boolean opens(String resource) {
      if (!governed) return true;
      List<OSDefinition> owners =
          catalogue.stream().filter(definition -> definition.includesModule(resource)).toList();
      return owners.isEmpty()
          || owners.stream().anyMatch(owner -> enabled.contains(owner.getOsCode()));
    }
  }

  @Override
  @Transactional(readOnly = true)
  public boolean isResourceOpen(UUID tenantId, String resource) {
    if (tenantId == null || resource == null || resource.isBlank()) return true;
    return access(tenantId).opens(resource);
  }

  @Override
  @Transactional(readOnly = true)
  public Set<String> closedResources(UUID tenantId) {
    if (tenantId == null) return Set.of();
    Access access = access(tenantId);
    if (!access.governed()) return Set.of();
    return access.catalogue().stream()
        .flatMap(definition -> definition.getIncludedModules().stream())
        .filter(module -> !module.endsWith(".*"))
        .filter(module -> !access.opens(module))
        .collect(Collectors.toCollection(TreeSet::new));
  }

  private Access access(UUID tenantId) {
    List<Subscription> rows = subscriptions.findByTenantId(tenantId);
    if (rows.isEmpty() || !tenantAccess.isWritable(tenantId)) {
      return new Access(false, Set.of(), List.of());
    }
    Set<String> enabled =
        rows.stream()
            .filter(Subscription::isActive)
            .map(Subscription::getOsCode)
            .collect(Collectors.toSet());
    // Ownership must not depend on whether the definition is still offered (is_active) or deleted:
    // a retired definition that dropped out of this list would leave its resources unclaimed, and
    // an unclaimed resource is open to everyone.
    List<OSDefinition> catalogue = definitions.findByTenantId(TenantContext.SYSTEM_TENANT_ID);
    return new Access(true, enabled, catalogue);
  }
}
